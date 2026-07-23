package dev.securecdms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

@Slf4j
@Service
public class ConversionService {

    private static final Set<String> CONVERTIBLE_TYPES = Set.of(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text",
            "application/rtf",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.presentation",
            "text/csv",
            "text/plain",
            "text/markdown",
            "text/html"
    );

    private static final String LIBRE_OFFICE_CMD = "libreoffice";

    private final String libreofficeUrl;
    private final HttpClient httpClient;

    public ConversionService(@Value("${app.conversion.libreoffice-url}") String libreofficeUrl) {
        this.libreofficeUrl = libreofficeUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    public boolean isConvertible(String contentType) {
        if (contentType == null) return false;
        if (contentType.startsWith("text/")) return true;
        return CONVERTIBLE_TYPES.contains(contentType);
    }

    public String renderToHtml(Path inputFile) throws IOException, InterruptedException {
        if (libreofficeUrl != null && !libreofficeUrl.isBlank()) {
            return renderViaSidecar(inputFile);
        }
        return renderViaCli(inputFile);
    }

    public Path saveFromHtml(String html, String originalFilename, Path tempDir) throws IOException, InterruptedException {
        if (libreofficeUrl != null && !libreofficeUrl.isBlank()) {
            return saveViaSidecar(html, originalFilename, tempDir);
        }
        return saveViaCli(html, originalFilename, tempDir);
    }

    private String renderViaSidecar(Path inputFile) throws IOException, InterruptedException {
        byte[] fileBytes = Files.readAllBytes(inputFile);
        String boundary = "----" + Long.toHexString(System.currentTimeMillis());

        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"input\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";

        byte[] headerBytes = header.getBytes();
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes();

        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(libreofficeUrl + "/convert/html"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("Sidecar conversion failed ({}): {}", response.statusCode(), response.body());
            return fallbackToText(inputFile);
        }
        return wrapHtml(response.body());
    }

    private Path saveViaSidecar(String html, String originalFilename, Path tempDir) throws IOException, InterruptedException {
        String extension = extractExtension(originalFilename);
        byte[] htmlBytes = html.getBytes();
        String boundary = "----" + Long.toHexString(System.currentTimeMillis());

        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"input." + extension + "\"\r\n"
                + "Content-Type: text/html\r\n\r\n";

        byte[] headerBytes = header.getBytes();
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes();

        byte[] body = new byte[headerBytes.length + htmlBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(htmlBytes, 0, body, headerBytes.length, htmlBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + htmlBytes.length, footerBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(libreofficeUrl + "/convert/from-html"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Sidecar save failed (" + response.statusCode() + "): " + new String(response.body()));
        }

        String targetName = "converted." + extension;
        Path target = tempDir.resolve(targetName);
        Files.write(target, response.body());
        return target;
    }

    private String renderViaCli(Path inputFile) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("conv-");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    LIBRE_OFFICE_CMD, "--headless", "--convert-to", "html",
                    "--outdir", tempDir.toString(),
                    inputFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                String error = new String(process.getInputStream().readAllBytes());
                log.warn("LibreOffice CLI conversion failed ({}): {}", exitCode, error);
                return fallbackToText(inputFile);
            }

            Path[] converted = Files.list(tempDir)
                    .filter(f -> f.toString().endsWith(".html"))
                    .toArray(Path[]::new);

            if (converted.length == 0) {
                return fallbackToText(inputFile);
            }

            String html = Files.readString(converted[0]);
            return wrapHtml(html);

        } finally {
            deleteDir(tempDir);
        }
    }

    private Path saveViaCli(String html, String originalFilename, Path tempDir) throws IOException, InterruptedException {
        Path htmlFile = tempDir.resolve("content.html");
        Files.writeString(htmlFile, html);

        String extension = extractExtension(originalFilename);
        String filter = getExportFilter(extension);

        ProcessBuilder pb = new ProcessBuilder(
                LIBRE_OFFICE_CMD, "--headless", "--convert-to", filter,
                "--outdir", tempDir.toString(),
                htmlFile.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            String error = new String(process.getInputStream().readAllBytes());
            throw new IOException("LibreOffice save failed: " + error);
        }

        Path[] converted = Files.list(tempDir)
                .filter(f -> !f.equals(htmlFile) && !f.toString().endsWith(".html"))
                .toArray(Path[]::new);

        if (converted.length == 0) {
            throw new IOException("LibreOffice produced no output file");
        }

        String targetName = "converted." + extension;
        Path target = tempDir.resolve(targetName);
        Files.move(converted[0], target);
        return target;
    }

    private String fallbackToText(Path inputFile) throws IOException {
        byte[] bytes = Files.readAllBytes(inputFile);
        if (bytes.length == 0) return "";
        for (byte b : bytes) {
            if (b == 0) {
                throw new IOException("Binary file detected (null byte), cannot read as text");
            }
        }
        String content = new String(bytes);
        if (content.isBlank()) return "";
        if (content.trim().startsWith("<")) {
            return content;
        }
        String escaped = escapeHtml(content);
        return "<p>" + escaped.replace("\n", "</p><p>") + "</p>";
    }

    private String wrapHtml(String body) {
        if (body == null) return "<html><body></body></html>";
        if (body.contains("<html") || body.contains("<body")) return body;
        return "<html><body>" + body + "</body></html>";
    }

    private String getExportFilter(String extension) {
        return switch (extension) {
            case "docx" -> "docx:Office Open XML Text";
            case "doc" -> "doc:MS Word 97";
            case "odt" -> "odt:writer8";
            case "rtf" -> "rtf:Rich Text Format";
            case "xlsx" -> "xlsx:Calc MS Excel 2007 XML";
            case "xls" -> "xls:MS Excel 97";
            case "ods" -> "ods:calc8";
            case "pptx" -> "pptx:Impress MS PowerPoint 2007 XML";
            case "ppt" -> "ppt:MS PowerPoint 97";
            case "odp" -> "odp:impress8";
            case "csv" -> "csv:Text - txt - csv (StarCalc)";
            case "md" -> "html";
            default -> extension;
        };
    }

    public String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "docx";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void deleteDir(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }
}

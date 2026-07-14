package dev.securecdms.service;

import dev.securecdms.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Service
public class StorageService {

    private final Path uploadDir;

    public StorageService(@Value("${app.storage.upload-dir}") String uploadDir) throws IOException {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
        log.info("Upload-Verzeichnis: {}", this.uploadDir);
    }

    public String store(MultipartFile file) throws IOException {
        // UUID als Dateiname — kein Path Traversal möglich
        String extension = getExtension(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path target = uploadDir.resolve(storedFilename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        log.debug("Datei gespeichert: {}", storedFilename);
        return storedFilename;
    }

    public Path load(String storedFilename) {
        Path file = uploadDir.resolve(storedFilename).normalize();

        // Sicherstellen dass die Datei noch im uploadDir liegt (kein Path Traversal)
        if (!file.startsWith(uploadDir)) {
            throw new SecurityException("Ungültiger Dateipfad");
        }
        if (!Files.exists(file)) {
            throw new ResourceNotFoundException("Datei nicht gefunden: " + storedFilename);
        }

        return file;
    }

    public void delete(String storedFilename) throws IOException {
        Path file = uploadDir.resolve(storedFilename).normalize();
        if (!file.startsWith(uploadDir)) {
            throw new SecurityException("Ungültiger Dateipfad");
        }
        Files.deleteIfExists(file);
        log.debug("Datei gelöscht: {}", storedFilename);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
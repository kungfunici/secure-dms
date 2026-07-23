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
        log.info("Upload directory: {}", this.uploadDir);
    }

    public String store(MultipartFile file) throws IOException {
        String extension = getExtension(file.getOriginalFilename());
        String storedFilename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);

        Path target = uploadDir.resolve(storedFilename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        log.debug("File stored: {}", storedFilename);
        return storedFilename;
    }

    public String storeWithName(MultipartFile file, String storedFilename) throws IOException {
        Path target = uploadDir.resolve(storedFilename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("File stored with custom name: {}", storedFilename);
        return storedFilename;
    }

    public Path load(String storedFilename) {
        Path file = uploadDir.resolve(storedFilename).normalize();

        if (!file.startsWith(uploadDir)) {
            throw new SecurityException("Invalid file path");
        }
        if (!Files.exists(file)) {
            throw new ResourceNotFoundException("File not found: " + storedFilename);
        }

        return file;
    }

    public void copy(String sourceFilename, String targetFilename) throws IOException {
        Path source = uploadDir.resolve(sourceFilename).normalize();
        if (!source.startsWith(uploadDir)) {
            throw new SecurityException("Invalid file path");
        }
        Path target = uploadDir.resolve(targetFilename).normalize();
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("File copied: {} -> {}", sourceFilename, targetFilename);
    }

    public void delete(String storedFilename) throws IOException {
        Path file = uploadDir.resolve(storedFilename).normalize();
        if (!file.startsWith(uploadDir)) {
            throw new SecurityException("Invalid file path");
        }
        Files.deleteIfExists(file);
        log.debug("File deleted: {}", storedFilename);
    }

    public void storeBytes(byte[] data, String storedFilename) throws IOException {
        Path target = uploadDir.resolve(storedFilename);
        Files.write(target, data);
        log.debug("Bytes stored: {}", storedFilename);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}

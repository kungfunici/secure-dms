package dev.securecdms.service;

import dev.securecdms.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageServiceTest {

    private StorageService storageService;
    private @TempDir Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        storageService = new StorageService(tempDir.toString());
    }

    @Test
    void store_shouldSaveFile() throws IOException {
        var file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        String storedName = storageService.store(file);

        assertNotNull(storedName);
        assertTrue(storedName.endsWith(".pdf"));
        assertTrue(Files.exists(tempDir.resolve(storedName)));
    }

    @Test
    void store_shouldGenerateUuidFilename() throws IOException {
        var file = new MockMultipartFile("file", "report.pdf", "application/pdf", "data".getBytes());

        String name1 = storageService.store(file);
        String name2 = storageService.store(file);

        assertNotEquals(name1, name2);
    }

    @Test
    void load_shouldReturnPath() throws IOException {
        var file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        String storedName = storageService.store(file);

        Path loaded = storageService.load(storedName);

        assertEquals(tempDir.resolve(storedName), loaded);
    }

    @Test
    void load_shouldThrowWhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> storageService.load("nonexistent.pdf"));
    }

    @Test
    void delete_shouldRemoveFile() throws IOException {
        var file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
        String storedName = storageService.store(file);

        storageService.delete(storedName);

        assertFalse(Files.exists(tempDir.resolve(storedName)));
    }

    @Test
    void delete_shouldNotThrowWhenFileMissing() {
        assertDoesNotThrow(() -> storageService.delete("nonexistent.pdf"));
    }

    @Test
    void store_shouldHandleFilenameWithoutExtension() throws IOException {
        var file = new MockMultipartFile("file", "noext", "application/octet-stream", "data".getBytes());

        String storedName = storageService.store(file);

        assertFalse(storedName.contains("."));
    }
}

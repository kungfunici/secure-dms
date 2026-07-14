package dev.securecdms.service;

import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentPermission;
import dev.securecdms.model.Role;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.FolderRepository;
import dev.securecdms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private StorageService storageService;
    @Mock private AuditService auditService;

    private DocumentService documentService;
    private User owner;
    private User otherUser;
    private Document document;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, userRepository, folderRepository, storageService, auditService);

        owner = User.builder().id(1L).username("owner").role(Role.ROLE_USER).build();
        otherUser = User.builder().id(2L).username("other").role(Role.ROLE_USER).build();

        document = Document.builder()
                .id(1L)
                .originalFilename("test.pdf")
                .storedFilename("uuid-test.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .description("Test document")
                .owner(owner)
                .permissions(new ArrayList<>())
                .build();
    }

    @Test
    void upload_shouldStoreFileAndSaveDocument() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(storageService.store(any())).thenReturn("stored-uuid.pdf");
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = documentService.upload(file, "Q2 report", null, "owner");

        assertEquals("report.pdf", response.getOriginalFilename());
        assertEquals("Q2 report", response.getDescription());
        assertEquals(owner.getUsername(), response.getOwnerUsername());
        verify(storageService).store(file);
        verify(documentRepository).save(any());
        verify(auditService).log(eq("UPLOAD"), eq(1L), any(), any(), eq(null));
    }

    @Test
    void upload_shouldRejectUnsupportedFileType() {
        MultipartFile file = new MockMultipartFile("file", "script.exe", "application/x-msdownload", "blah".getBytes());

        assertThrows(IllegalArgumentException.class, () -> documentService.upload(file, null, null, "owner"));
    }

    @Test
    void listMyDocuments_shouldReturnOwnedDocuments() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findByOwner(eq(owner), any()))
                .thenReturn(new PageImpl<>(List.of(document)));

        Page<dev.securecdms.dto.response.DocumentResponse> result =
                documentService.listMyDocuments("owner", PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
        assertEquals("test.pdf", result.getContent().getFirst().getOriginalFilename());
    }

    @Test
    void download_shouldAllowOwner() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(storageService.load("uuid-test.pdf")).thenReturn(Path.of("/tmp/uuid-test.pdf"));

        var result = documentService.download(1L, "owner");

        assertEquals("test.pdf", result.originalFilename());
        verify(auditService).log(eq("DOWNLOAD"), eq(1L), eq(1L), any(), eq(null));
    }

    @Test
    void download_shouldAllowUserWithReadPermission() {
        DocumentPermission perm = DocumentPermission.builder()
                .user(otherUser).permissionType(DocumentPermission.PermissionType.READ).build();
        document.getPermissions().add(perm);

        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(storageService.load("uuid-test.pdf")).thenReturn(Path.of("/tmp/uuid-test.pdf"));

        var result = documentService.download(1L, "other");

        assertEquals("test.pdf", result.originalFilename());
    }

    @Test
    void download_shouldDenyUserWithoutPermission() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.download(1L, "other"));
    }

    @Test
    void delete_shouldAllowOwner() throws IOException {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        documentService.delete(1L, "owner");

        verify(storageService).delete("uuid-test.pdf");
        verify(documentRepository).delete(document);
        verify(auditService).log(eq("DELETE"), eq(1L), eq(1L), any(), eq(null));
    }

    @Test
    void delete_shouldRemovePermissionForNonOwner() throws IOException {
        DocumentPermission perm = DocumentPermission.builder()
                .user(otherUser).permissionType(DocumentPermission.PermissionType.READ).build();
        document.getPermissions().add(perm);

        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        documentService.delete(1L, "other");

        assertTrue(document.getPermissions().isEmpty());
        verify(documentRepository).save(document);
        verify(auditService).log(eq("UNSHARE"), eq(2L), eq(1L), any(), eq(null));
        verify(storageService, never()).delete(any());
        verify(documentRepository, never()).delete(any());
    }

    @Test
    void delete_shouldThrowWhenNonOwnerHasNoPermission() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.delete(1L, "other"));
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> documentService.delete(99L, "owner"));
    }

    @Test
    void update_shouldUpdateDescription() throws IOException {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = documentService.update(1L, "owner", null, "Updated description");

        assertEquals("Updated description", response.getDescription());
        verify(auditService).log(eq("UPDATE"), eq(1L), eq(1L), any(), eq(null));
    }

    @Test
    void update_shouldReplaceFile() throws IOException {
        MultipartFile newFile = new MockMultipartFile("file", "new.pdf", "application/pdf", "new content".getBytes());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(storageService.store(any())).thenReturn("new-uuid.pdf");
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = documentService.update(1L, "owner", newFile, null);

        assertEquals("new.pdf", response.getOriginalFilename());
        verify(storageService).delete("uuid-test.pdf");
        verify(storageService).store(newFile);
    }

    @Test
    void update_shouldDenyUserWithoutWritePermission() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.update(1L, "other", null, "hacked"));
    }
}

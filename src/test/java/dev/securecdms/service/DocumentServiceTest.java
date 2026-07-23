package dev.securecdms.service;

import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentPermission;
import dev.securecdms.model.DocumentVersion;
import dev.securecdms.model.RecentlyViewed;
import dev.securecdms.model.Role;
import dev.securecdms.model.Tag;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.DocumentVersionRepository;
import dev.securecdms.repository.FolderRepository;
import dev.securecdms.repository.RecentlyViewedRepository;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    @Mock private RecentlyViewedRepository recentlyViewedRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private TextExtractionService textExtractionService;
    @Mock private dev.securecdms.repository.FavoriteRepository favoriteRepository;
    @Mock private dev.securecdms.repository.TagRepository tagRepository;
    @Mock private RetentionService retentionService;
    @Mock private WebhookService webhookService;
    @Mock private ConversionService conversionService;

    private DocumentService documentService;
    private User owner;
    private User otherUser;
    private Document document;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, userRepository, folderRepository, storageService, auditService, recentlyViewedRepository, documentVersionRepository, textExtractionService, favoriteRepository, tagRepository, retentionService, webhookService, conversionService);

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

        var response = documentService.upload(file, "Q2 report", null, null, "owner");

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

        assertThrows(IllegalArgumentException.class, () -> documentService.upload(file, null, null, null, "owner"));
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
    void delete_shouldSoftDeleteForOwner() throws IOException {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        documentService.delete(1L, "owner");

        assertNotNull(document.getDeletedAt());
        verify(documentRepository).save(document);
        verify(auditService).log(eq("TRASH"), eq(1L), eq(1L), any(), eq(null));
        verify(storageService, never()).delete(any());
        verify(documentRepository, never()).delete(any());
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

    @Test
    void upload_shouldAutoDetectDocumentType() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(storageService.store(any())).thenReturn("stored-uuid.pdf");
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = documentService.upload(file, null, null, null, "owner");

        assertEquals("PDF", response.getDocumentType());
    }

    @Test
    void upload_shouldUseExplicitDocumentType() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "content".getBytes());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(storageService.store(any())).thenReturn("stored-uuid.pdf");
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = documentService.upload(file, null, "Word", null, "owner");

        assertEquals("Word", response.getDocumentType());
    }

    @Test
    void upload_shouldAutoDetectFromAllowedButUnmappedMime() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "data.bin", "text/csv", "a,b,c".getBytes());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(storageService.store(any())).thenReturn("stored-uuid.csv");
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = documentService.upload(file, null, null, null, "owner");

        assertEquals("CSV", response.getDocumentType());
    }

    @Test
    void delete_shouldPermanentlyDeleteWhenAlreadyInTrash() throws IOException {
        document.setDeletedAt(java.time.Instant.now());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        documentService.delete(1L, "owner");

        verify(storageService).delete("uuid-test.pdf");
        verify(documentRepository).delete(document);
        verify(auditService).log(eq("DELETE"), eq(1L), eq(1L), any(), eq(null));
    }

    @Test
    void restore_shouldClearDeletedAt() {
        document.setDeletedAt(java.time.Instant.now());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = documentService.restore(1L, "owner");

        assertNull(response.getDeletedAt());
        verify(auditService).log(eq("RESTORE"), eq(1L), eq(1L), any(), eq(null));
    }

    @Test
    void restore_shouldThrowForNonOwner() {
        document.setDeletedAt(java.time.Instant.now());

        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.restore(1L, "other"));
    }

    @Test
    void restore_shouldThrowWhenNotInTrash() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(IllegalArgumentException.class, () -> documentService.restore(1L, "owner"));
    }

    @Test
    void download_shouldTrackRecentlyViewed() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(storageService.load("uuid-test.pdf")).thenReturn(Path.of("/tmp/uuid-test.pdf"));
        when(recentlyViewedRepository.findByUserIdAndDocumentId(1L, 1L)).thenReturn(Optional.empty());

        documentService.download(1L, "owner");

        verify(recentlyViewedRepository).save(any(RecentlyViewed.class));
    }

    @Test
    void download_shouldThrowForDeletedDocument() {
        document.setDeletedAt(java.time.Instant.now());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.download(1L, "owner"));
    }

    @Test
    void listTrash_shouldReturnDeletedDocuments() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findTrashByOwner(eq(owner), any()))
                .thenReturn(new PageImpl<>(List.of(document)));

        var result = documentService.listTrash("owner", null, PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
    }

    @Test
    void update_shouldThrowForDeletedDocument() {
        document.setDeletedAt(java.time.Instant.now());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.update(1L, "owner", null, "updated"));
    }

    @Test
    void moveToFolder_shouldThrowForDeletedDocument() {
        document.setDeletedAt(java.time.Instant.now());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.moveToFolder(1L, 2L, "owner"));
    }

    @Test
    void search_shouldExcludeDeletedDocuments() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.searchOwnedAndShared(eq(owner), eq("test"), any()))
                .thenReturn(new PageImpl<>(List.of(document)));

        var result = documentService.search("owner", "test", PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
    }

    @Test
    void listSharedDocuments_shouldExcludeDeleted() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findSharedWithUser(eq(owner), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = documentService.listSharedWithMe("owner", null, PageRequest.of(0, 10));

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void upload_shouldCreateVersion() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(storageService.store(any())).thenReturn("stored-uuid.pdf");
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        documentService.upload(file, null, null, null, "owner");

        verify(documentVersionRepository).save(any());
    }

    @Test
    void upload_shouldExtractText() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(storageService.store(any())).thenReturn("stored-uuid.pdf");
        when(storageService.load(anyString())).thenReturn(Path.of("/tmp/stored-uuid.pdf"));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(textExtractionService.extractText(any())).thenReturn("extracted text");

        var response = documentService.upload(file, null, null, null, "owner");

        verify(textExtractionService).extractText(any());
    }

    @Test
    void getVersions_shouldReturnVersions() {
        DocumentVersion version = DocumentVersion.builder()
                .id(1L).document(document).versionNumber(1).fileSize(100L)
                .contentType("application/pdf").uploadedBy(owner)
                .createdAt(java.time.Instant.now()).build();

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document))
                .thenReturn(List.of(version));

        var result = documentService.getVersions(1L, "owner");

        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().getVersionNumber());
    }

    @Test
    void getVersions_shouldDenyAccess() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.getVersions(1L, "other"));
    }

    @Test
    void preview_shouldReturnPath() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(storageService.load("uuid-test.pdf")).thenReturn(Path.of("/tmp/uuid-test.pdf"));

        var result = documentService.preview(1L, "owner");

        assertEquals("uuid-test.pdf", result.path().getFileName().toString());
    }

    @Test
    void preview_shouldDenyWithoutAccess() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.preview(1L, "other"));
    }

    @Test
    void preview_shouldDenyForDeletedDocument() {
        document.setDeletedAt(java.time.Instant.now());

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.preview(1L, "owner"));
    }

    @Test
    void batchDelete_shouldCallDeleteForEach() throws IOException {
        Document doc2 = Document.builder().id(2L).owner(owner).build();

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));

        documentService.batchDelete(List.of(1L, 2L), "owner");

        assertNotNull(document.getDeletedAt());
        assertNotNull(doc2.getDeletedAt());
    }

    @Test
    void batchMove_shouldCallMoveForEach() {
        Document doc2 = Document.builder().id(2L).owner(owner).build();

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc2));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        documentService.batchMove(List.of(1L, 2L), null, "owner");

        verify(documentRepository, times(2)).save(any());
    }

    @Test
    void search_shouldAlsoSearchInExtractedText() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.searchOwnedAndShared(eq(owner), eq("content"), any()))
                .thenReturn(new PageImpl<>(List.of(document)));

        var result = documentService.search("owner", "content", PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
    }

    // ---- Tag tests ----

    @Test
    void addTag_shouldAddTagToDocument() {
        Tag tag = Tag.builder().id(1L).name("important").build();

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));

        var response = documentService.addTag(1L, 1L, "owner");

        assertTrue(response.getTags().contains("important"));
        verify(documentRepository).save(document);
    }

    @Test
    void addTag_shouldDenyNonOwner() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.addTag(1L, 1L, "other"));
    }

    @Test
    void removeTag_shouldRemoveTagFromDocument() {
        Tag tag = Tag.builder().id(1L).name("important").build();
        document.setTags(new HashSet<>(Set.of(tag)));

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = documentService.removeTag(1L, 1L, "owner");

        assertTrue(response.getTags().isEmpty());
    }

    @Test
    void removeTag_shouldDenyNonOwner() {
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class, () -> documentService.removeTag(1L, 1L, "other"));
    }

    @Test
    void duplicate_shouldCopyTags() throws IOException {
        Tag tag = Tag.builder().id(1L).name("important").build();
        document.setTags(new HashSet<>(Set.of(tag)));

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        doNothing().when(storageService).copy(anyString(), anyString());
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.findByOwnerAndOriginalFilename(eq(owner), anyString())).thenReturn(Optional.empty());

        var response = documentService.duplicate(1L, "owner");

        assertTrue(response.getTags().contains("important"));
    }

    @Test
    void delete_shouldThrowIfLegalHoldActive() {
        document.setLegalHold(true);
        document.setDeletedAt(Instant.now());
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

        assertThrows(AccessDeniedException.class, () -> documentService.delete(1L, "owner"));
        verify(documentRepository, never()).delete(any());
    }

    @Test
    void delete_shouldAllowWhenLegalHoldInactive() throws IOException {
        document.setDeletedAt(Instant.now());
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        doNothing().when(storageService).delete(anyString());

        documentService.delete(1L, "owner");

        verify(documentRepository).delete(document);
        verify(storageService).delete(document.getStoredFilename());
    }
}

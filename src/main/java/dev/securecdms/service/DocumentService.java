package dev.securecdms.service;

import dev.securecdms.dto.response.DocumentResponse;
import dev.securecdms.dto.response.VersionResponse;
import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentPermission;
import dev.securecdms.model.DocumentVersion;
import dev.securecdms.model.Favorite;
import dev.securecdms.model.Folder;
import dev.securecdms.model.RecentlyViewed;
import dev.securecdms.model.Tag;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.DocumentVersionRepository;
import dev.securecdms.repository.FavoriteRepository;
import dev.securecdms.repository.FolderRepository;
import dev.securecdms.repository.RecentlyViewedRepository;
import dev.securecdms.repository.TagRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final RecentlyViewedRepository recentlyViewedRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final TextExtractionService textExtractionService;
    private final FavoriteRepository favoriteRepository;
    private final TagRepository tagRepository;
    private final RetentionService retentionService;
    private final WebhookService webhookService;
    private final ConversionService conversionService;

    private static final Map<String, String> MIME_TO_DOC_TYPE = Map.ofEntries(
            Map.entry("application/pdf", "PDF"),
            Map.entry("application/msword", "Word"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word"),
            Map.entry("application/vnd.ms-excel", "Excel"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel"),
            Map.entry("application/vnd.ms-powerpoint", "PowerPoint"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "PowerPoint"),
            Map.entry("application/vnd.oasis.opendocument.text", "Word"),
            Map.entry("application/vnd.oasis.opendocument.spreadsheet", "Excel"),
            Map.entry("application/vnd.oasis.opendocument.presentation", "PowerPoint"),
            Map.entry("application/rtf", "Word"),
            Map.entry("text/plain", "Text"),
            Map.entry("text/csv", "CSV"),
            Map.entry("text/markdown", "Text"),
            Map.entry("text/html", "Text"),
            Map.entry("text/xml", "Text"),
            Map.entry("application/json", "Text"),
            Map.entry("application/xml", "Text"),
            Map.entry("image/png", "Image"),
            Map.entry("image/jpeg", "Image"),
            Map.entry("image/gif", "Image"),
            Map.entry("image/webp", "Image"),
            Map.entry("image/svg+xml", "Image"),
            Map.entry("image/bmp", "Image")
    );

    @Transactional
    public DocumentResponse upload(MultipartFile file, String description, String documentType, Long folderId, String username) throws IOException {
        validateFileType(file);
        User owner = getUser(username);

        Folder folder = null;
        if (folderId != null) {
            folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
            if (!folder.getOwner().getId().equals(owner.getId())) {
                throw new AccessDeniedException("Access denied to folder");
            }
        }

        String storedFilename = storageService.store(file);

        String type = documentType != null ? documentType : MIME_TO_DOC_TYPE.getOrDefault(file.getContentType(), "Other");

        String extractedText = extractText(storedFilename);

        Document doc = Document.builder()
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFilename)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .description(description)
                .documentType(type)
                .owner(owner)
                .folder(folder)
                .extractedText(extractedText)
                .currentVersion(1)
                .build();

        documentRepository.save(doc);

        retentionService.applyPolicy(doc);

        saveVersion(doc, 1, storedFilename, file.getSize(), file.getContentType(), owner);

        log.info("Document uploaded: {} by {}", file.getOriginalFilename(), username);

        auditService.log("UPLOAD", owner.getId(), doc.getId(),
                "Uploaded: " + file.getOriginalFilename(), null);

        webhookService.dispatch("UPLOAD", doc.getId(), "Uploaded: " + file.getOriginalFilename(), username);

        return toResponse(doc, owner);
    }

    @Transactional
    public DocumentResponse update(Long documentId, String username, MultipartFile file, String description) throws IOException {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Cannot update a deleted document");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canWrite = hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canWrite) {
            throw new AccessDeniedException("Only the owner or a user with WRITE permission can update this document");
        }

        if (file != null && !file.isEmpty()) {
            validateFileType(file);

            int nextVersion = doc.getCurrentVersion() + 1;
            saveVersion(doc, nextVersion, doc.getStoredFilename(), doc.getFileSize(), doc.getContentType(), doc.getOwner());

            storageService.delete(doc.getStoredFilename());
            String newStoredFilename = storageService.store(file);
            doc.setStoredFilename(newStoredFilename);
            doc.setOriginalFilename(file.getOriginalFilename());
            doc.setContentType(file.getContentType());
            doc.setFileSize(file.getSize());
            doc.setCurrentVersion(nextVersion);

            String extractedText = extractText(newStoredFilename);
            doc.setExtractedText(extractedText);
        }

        if (description != null) {
            doc.setDescription(description);
        }

        documentRepository.save(doc);
        log.info("Document updated: {} by {}", doc.getOriginalFilename(), username);

        auditService.log("UPDATE", user.getId(), documentId,
                "Updated: " + doc.getOriginalFilename(), null);

        webhookService.dispatch("UPDATE", documentId, "Updated: " + doc.getOriginalFilename(), username);

        return toResponse(doc, user);
    }

    @Transactional(readOnly = true)
    public List<VersionResponse> getVersions(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);
        checkAccess(doc, user);

        return documentVersionRepository.findByDocumentOrderByVersionNumberDesc(doc).stream()
                .map(v -> VersionResponse.builder()
                        .id(v.getId())
                        .versionNumber(v.getVersionNumber())
                        .fileSize(v.getFileSize())
                        .contentType(v.getContentType())
                        .uploadedByUsername(v.getUploadedBy() != null ? v.getUploadedBy().getUsername() : null)
                        .createdAt(v.getCreatedAt())
                        .build())
                .toList();
    }

    @Transactional
    public DocumentResponse restoreVersion(Long documentId, Long versionId, String username) throws IOException {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Cannot restore version of a deleted document");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canWrite = hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);
        if (!isOwner && !canWrite) {
            throw new AccessDeniedException("Only the owner or a user with WRITE permission can restore versions");
        }

        DocumentVersion version = documentVersionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found: " + versionId));

        if (!version.getDocument().getId().equals(documentId)) {
            throw new IllegalArgumentException("Version does not belong to this document");
        }

        int nextVersion = doc.getCurrentVersion() + 1;
        saveVersion(doc, nextVersion, doc.getStoredFilename(), doc.getFileSize(), doc.getContentType(), doc.getOwner());

        storageService.delete(doc.getStoredFilename());

        String versionFile = version.getStoredFilename();
        if (!versionFile.startsWith("v" + version.getVersionNumber() + "-")) {
            try {
                storageService.load(versionFile);
            } catch (ResourceNotFoundException e) {
                versionFile = "v" + version.getVersionNumber() + "-" + versionFile;
            }
        }
        String restoredFilename = UUID.randomUUID() + "." + getExtension(versionFile);
        storageService.copy(versionFile, restoredFilename);
        doc.setStoredFilename(restoredFilename);
        doc.setFileSize(version.getFileSize());
        doc.setContentType(version.getContentType());
        doc.setCurrentVersion(nextVersion);

        String extractedText = extractText(restoredFilename);
        doc.setExtractedText(extractedText);

        documentRepository.save(doc);

        auditService.log("RESTORE_VERSION", user.getId(), documentId,
                "Restored version " + version.getVersionNumber() + ": " + doc.getOriginalFilename(), null);

        return toResponse(doc, user);
    }

    @Transactional
    public DocumentResponse moveToFolder(Long documentId, Long folderId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Cannot move a deleted document");
        }

        if (!doc.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the owner can move this document");
        }

        if (folderId != null) {
            Folder folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));
            if (!folder.getOwner().getId().equals(user.getId())) {
                throw new AccessDeniedException("Access denied to folder");
            }
            doc.setFolder(folder);
        } else {
            doc.setFolder(null);
        }

        documentRepository.save(doc);
        return toResponse(doc, user);
    }

    // ---- Batch operations ----

    @Transactional
    public void batchDelete(List<Long> documentIds, String username) throws IOException {
        User user = getUser(username);
        for (Long id : documentIds) {
            delete(id, username);
        }
        auditService.log("BATCH_DELETE", user.getId(), null,
                "Batch deleted " + documentIds.size() + " documents", null);
    }

    @Transactional
    public void emptyTrash(String username) throws IOException {
        User user = getUser(username);
        Page<Document> trashPage = documentRepository.findTrashByOwner(user, PageRequest.of(0, 1000));
        List<Long> ids = trashPage.getContent().stream().map(Document::getId).toList();
        if (ids.isEmpty()) return;
        for (Long id : ids) {
            delete(id, username);
        }
        auditService.log("BATCH_DELETE", user.getId(), null,
                "Emptied trash: " + ids.size() + " documents permanently deleted", null);
    }

    @Transactional
    public void batchMove(List<Long> documentIds, Long folderId, String username) {
        User user = getUser(username);
        for (Long id : documentIds) {
            moveToFolder(id, folderId, username);
        }
        auditService.log("BATCH_MOVE", user.getId(), null,
                "Batch moved " + documentIds.size() + " documents to folder " + folderId, null);
    }

    @Transactional(readOnly = true)
    public void batchDownload(List<Long> documentIds, String username, OutputStream outputStream) throws IOException {
        User user = getUser(username);
        int downloaded = 0;
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            for (Long id : documentIds) {
                try {
                    Document doc = getDocument(id);
                    checkAccess(doc, user);

                    if (doc.getDeletedAt() != null) continue;

                    String filename = doc.getOriginalFilename();
                    if (filename == null || filename.isBlank()) filename = "document_" + id;

                    Path filePath = storageService.load(doc.getStoredFilename());
                    ZipEntry entry = new ZipEntry(filename);
                    if (doc.getFileSize() != null && doc.getFileSize() > 0) {
                        entry.setSize(doc.getFileSize());
                    }
                    zos.putNextEntry(entry);
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                    downloaded++;
                } catch (Exception e) {
                    log.warn("Failed to add document {} to batch download: {}", id, e.getMessage());
                }
            }
        }
        auditService.log("BATCH_DOWNLOAD", user.getId(), null,
                "Batch downloaded " + downloaded + " document(s)", null);
    }

    // ---- Preview ----

    @Transactional(readOnly = true)
    public DownloadResult preview(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Document is in trash");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canRead = hasPermission(doc, user, DocumentPermission.PermissionType.READ)
                       || hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canRead) {
            throw new AccessDeniedException("Access denied to document " + documentId);
        }

        return new DownloadResult(
                storageService.load(doc.getStoredFilename()),
                doc.getOriginalFilename());
    }

    // ---- Text Content Editing ----

    @Transactional(readOnly = true)
    public String getContent(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Document is in trash");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canRead = hasPermission(doc, user, DocumentPermission.PermissionType.READ)
                       || hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canRead) {
            throw new AccessDeniedException("Access denied to document " + documentId);
        }

        if (!isTextContent(doc.getContentType())) {
            throw new IllegalArgumentException("Document type is not editable as text: " + doc.getContentType());
        }

        try {
            Path filePath = storageService.load(doc.getStoredFilename());
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read document content", e);
        }
    }

    @Transactional
    public DocumentResponse updateContent(Long documentId, String content, String username) throws IOException {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Cannot update a deleted document");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canWrite = hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canWrite) {
            throw new AccessDeniedException("Only the owner or a user with WRITE permission can edit this document");
        }

        if (!isTextContent(doc.getContentType())) {
            throw new IllegalArgumentException("Document type is not editable as text: " + doc.getContentType());
        }

        int nextVersion = doc.getCurrentVersion() + 1;
        saveVersion(doc, nextVersion, doc.getStoredFilename(), doc.getFileSize(), doc.getContentType(), user);

        Path filePath = storageService.load(doc.getStoredFilename());
        Files.writeString(filePath, content);

        doc.setCurrentVersion(nextVersion);
        doc.setExtractedText(content);
        documentRepository.save(doc);

        auditService.log("EDIT", user.getId(), documentId,
                "Edited content: " + doc.getOriginalFilename(), null);

        return toResponse(doc, user);
    }

    // ---- Render (convert to HTML for editor) ----

    @Transactional(readOnly = true)
    public String getRender(Long documentId, String username) throws Exception {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Document is in trash");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canRead = hasPermission(doc, user, DocumentPermission.PermissionType.READ)
                       || hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canRead) {
            throw new AccessDeniedException("Access denied to document " + documentId);
        }

        if (!conversionService.isConvertible(doc.getContentType())) {
            throw new IllegalArgumentException("Document type is not editable: " + doc.getContentType());
        }

        if (doc.getContentType().startsWith("text/")) {
            Path filePath = storageService.load(doc.getStoredFilename());
            String content = Files.readString(filePath);
            if (content.isBlank()) return "";
            if (content.trim().startsWith("<")) {
                return content;
            }
            String escaped = content
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
            return "<p>" + escaped.replace("\n", "</p><p>") + "</p>";
        }

        Path filePath = storageService.load(doc.getStoredFilename());
        return conversionService.renderToHtml(filePath);
    }

    @Transactional
    public DocumentResponse saveRender(Long documentId, String html, String username) throws Exception {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Cannot update a deleted document");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canWrite = hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canWrite) {
            throw new AccessDeniedException("Only the owner or a user with WRITE permission can edit this document");
        }

        if (!conversionService.isConvertible(doc.getContentType())) {
            throw new IllegalArgumentException("Document type is not editable: " + doc.getContentType());
        }

        if (doc.getContentType().startsWith("text/")) {
            return updateContent(documentId, html, username);
        }

        Path tempDir = Files.createTempDirectory("save-render-");
        try {
            Path converted = conversionService.saveFromHtml(html, doc.getOriginalFilename(), tempDir);

            int nextVersion = doc.getCurrentVersion() + 1;
            saveVersion(doc, nextVersion, doc.getStoredFilename(), doc.getFileSize(), doc.getContentType(), user);

            storageService.delete(doc.getStoredFilename());
            String newFilename = UUID.randomUUID() + "." + conversionService.extractExtension(doc.getOriginalFilename());
            storageService.storeBytes(Files.readAllBytes(converted), newFilename);
            doc.setStoredFilename(newFilename);
            doc.setFileSize(Files.size(converted));
            doc.setCurrentVersion(nextVersion);
            documentRepository.save(doc);

            auditService.log("EDIT", user.getId(), documentId,
                    "Edited content: " + doc.getOriginalFilename(), null);
            webhookService.dispatch("UPDATE", documentId, "Edited: " + doc.getOriginalFilename(), username);

            return toResponse(doc, user);
        } finally {
            deleteDir(tempDir);
        }
    }

    private void deleteDir(Path dir) {
        try (var files = Files.walk(dir)) {
            files.sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    private boolean isTextContent(String contentType) {
        return contentType != null && (contentType.startsWith("text/")
                || contentType.equals("application/json")
                || contentType.equals("application/xml"));
    }

    // ---- Standard CRUD ----

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listMyDocuments(String username, Pageable pageable) {
        User owner = getUser(username);
        return documentRepository.findByOwner(owner, pageable).map(d -> toResponse(d, owner));
    }

    @Transactional(readOnly = true)
    public DocumentResponse getById(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);
        return toResponse(doc, user);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listTrash(String username, String query, Pageable pageable) {
        User owner = getUser(username);
        if (query != null && !query.isBlank()) {
            return documentRepository.searchTrashByOwner(owner, query, pageable).map(d -> toResponse(d, owner));
        }
        return documentRepository.findTrashByOwner(owner, pageable).map(d -> toResponse(d, owner));
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(String username, String query, Pageable pageable) {
        User user = getUser(username);
        return documentRepository.searchOwnedAndShared(user, query, pageable).map(d -> toResponse(d, user));
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listRecentlyViewed(String username, Pageable pageable) {
        User user = getUser(username);
        List<RecentlyViewed> recent = recentlyViewedRepository.findByUserOrderByViewedAtDesc(user);
        List<Long> docIds = recent.stream()
                .map(r -> r.getDocument().getId())
                .toList();
        if (docIds.isEmpty()) {
            return Page.empty();
        }
        return documentRepository.findAllById(docIds)
                .stream()
                .filter(d -> d.getDeletedAt() == null)
                .map(d -> toResponse(d, user))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        list -> new org.springframework.data.domain.PageImpl<>(list, pageable, list.size())));
    }

    @Transactional
    public DownloadResult download(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Document is in trash");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canRead = hasPermission(doc, user, DocumentPermission.PermissionType.READ)
                       || hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canRead) {
            throw new AccessDeniedException("Access denied to document " + documentId);
        }

        recentlyViewedRepository.findByUserIdAndDocumentId(user.getId(), documentId)
                .ifPresentOrElse(
                        rv -> { rv.setViewedAt(Instant.now()); recentlyViewedRepository.save(rv); },
                        () -> {
                            RecentlyViewed rv = RecentlyViewed.builder()
                                    .user(user).document(doc).viewedAt(Instant.now()).build();
                            recentlyViewedRepository.save(rv);
                        });

        auditService.log("DOWNLOAD", user.getId(), doc.getId(),
                "Downloaded: " + doc.getOriginalFilename(), null);

        return new DownloadResult(
                storageService.load(doc.getStoredFilename()),
                doc.getOriginalFilename());
    }

    public record DownloadResult(Path path, String originalFilename) {}

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listSharedWithMe(String username, String query, Pageable pageable) {
        User user = getUser(username);
        if (query != null && !query.isBlank()) {
            return documentRepository.searchSharedWithUser(user, query, pageable).map(d -> toResponse(d, user));
        }
        return documentRepository.findSharedWithUser(user, pageable).map(d -> toResponse(d, user));
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listSharedByMe(String username, String query, Pageable pageable) {
        User user = getUser(username);
        if (query != null && !query.isBlank()) {
            return documentRepository.searchSharedByOwner(user, query, pageable).map(d -> toResponse(d, user));
        }
        return documentRepository.findSharedByOwner(user, pageable).map(d -> toResponse(d, user));
    }

    @Transactional
    public void delete(Long documentId, String username) throws IOException {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        boolean isOwner = doc.getOwner().getId().equals(user.getId());

        if (isOwner) {
            if (doc.getDeletedAt() == null) {
                doc.setDeletedAt(Instant.now());
                documentRepository.save(doc);
                favoriteRepository.findByUserAndDocument(user, doc)
                        .ifPresent(f -> favoriteRepository.delete(f));
                auditService.log("TRASH", user.getId(), documentId,
                        "Moved to trash: " + doc.getOriginalFilename(), null);
                webhookService.dispatch("TRASH", documentId, "Moved to trash: " + doc.getOriginalFilename(), username);
                log.info("Document moved to trash: {} by {}", doc.getOriginalFilename(), username);
            } else {
                if (doc.getLegalHold()) {
                    throw new AccessDeniedException("Cannot delete document with active legal hold");
                }
                storageService.delete(doc.getStoredFilename());
                documentRepository.delete(doc);
                auditService.log("DELETE", user.getId(), documentId,
                        "Permanently deleted: " + doc.getOriginalFilename(), null);
                webhookService.dispatch("DELETE", documentId, "Permanently deleted: " + doc.getOriginalFilename(), username);
                log.info("Document permanently deleted: {} by {}", doc.getOriginalFilename(), username);
            }
        } else {
            boolean hadPermission = doc.getPermissions().removeIf(p -> p.getUser().getId().equals(user.getId()));
            if (!hadPermission) {
                throw new AccessDeniedException("Access denied to document " + documentId);
            }
            documentRepository.save(doc);
            auditService.log("UNSHARE", user.getId(), documentId,
                    "Removed own access to: " + doc.getOriginalFilename(), null);
            log.info("User {} removed own access to document {}", username, documentId);
        }
    }

    @Transactional
    public DocumentResponse restore(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (!doc.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the owner can restore this document");
        }

        if (doc.getDeletedAt() == null) {
            throw new IllegalArgumentException("Document is not in trash");
        }

        doc.setDeletedAt(null);
        documentRepository.save(doc);

        auditService.log("RESTORE", user.getId(), documentId,
                "Restored: " + doc.getOriginalFilename(), null);
        log.info("Document restored: {} by {}", doc.getOriginalFilename(), username);

        return toResponse(doc, user);
    }

    // ---- Duplicate ----

    @Transactional
    public DocumentResponse duplicate(Long documentId, String username) throws IOException {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (doc.getDeletedAt() != null) {
            throw new AccessDeniedException("Cannot duplicate a deleted document");
        }

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canRead = hasPermission(doc, user, DocumentPermission.PermissionType.READ)
                       || hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);
        if (!isOwner && !canRead) {
            throw new AccessDeniedException("Access denied");
        }

        String newFilename = UUID.randomUUID() + "." + getExtension(doc.getOriginalFilename());
        storageService.copy(doc.getStoredFilename(), newFilename);

        String baseName = doc.getOriginalFilename();
        String dot = baseName.contains(".") ? baseName.substring(baseName.lastIndexOf('.')) : "";
        String nameWithoutExt = dot.isEmpty() ? baseName : baseName.substring(0, baseName.lastIndexOf('.'));
        String newName = nameWithoutExt + " (Copy)" + dot;

        int count = 2;
        while (documentRepository.findByOwnerAndOriginalFilename(user, newName).isPresent()) {
            newName = nameWithoutExt + " (Copy " + count++ + ")" + dot;
        }

        Document copy = Document.builder()
                .originalFilename(newName)
                .storedFilename(newFilename)
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .description(doc.getDescription())
                .documentType(doc.getDocumentType())
                .owner(user)
                .folder(doc.getFolder())
                .extractedText(doc.getExtractedText())
                .currentVersion(1)
                .tags(new HashSet<>(doc.getTags()))
                .build();

        documentRepository.save(copy);

        auditService.log("DUPLICATE", user.getId(), copy.getId(),
                "Duplicated: " + doc.getOriginalFilename() + " -> " + newName, null);

        return toResponse(copy, user);
    }

    // ---- Tags ----

    @Transactional
    public DocumentResponse addTag(Long documentId, Long tagId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (!doc.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the owner can tag documents");
        }

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + tagId));

        doc.getTags().add(tag);
        documentRepository.save(doc);
        return toResponse(doc, user);
    }

    @Transactional
    public DocumentResponse removeTag(Long documentId, Long tagId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (!doc.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the owner can remove tags");
        }

        doc.getTags().removeIf(t -> t.getId().equals(tagId));
        documentRepository.save(doc);
        return toResponse(doc, user);
    }

    // ---- Favorites ----

    @Transactional
    public boolean toggleFavorite(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);
        checkAccess(doc, user);

        var existing = favoriteRepository.findByUserAndDocument(user, doc);
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        } else {
            favoriteRepository.save(Favorite.builder().user(user).document(doc).build());
            return true;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listFavorites(String username) {
        User user = getUser(username);
        return favoriteRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(f -> toResponse(f.getDocument(), user))
                .toList();
    }

    // ---- Helpers ----

    private String extractText(String storedFilename) {
        try {
            Path filePath = storageService.load(storedFilename);
            return textExtractionService.extractText(filePath);
        } catch (Exception e) {
            log.warn("Text extraction failed for {}: {}", storedFilename, e.getMessage());
            return null;
        }
    }

    private void saveVersion(Document doc, int versionNumber, String storedFilename, Long fileSize, String contentType, User uploadedBy) throws IOException {
        String versionFilename = "v" + versionNumber + "-" + storedFilename;
        storageService.copy(storedFilename, versionFilename);
        DocumentVersion version = DocumentVersion.builder()
                .document(doc)
                .versionNumber(versionNumber)
                .storedFilename(versionFilename)
                .fileSize(fileSize)
                .contentType(contentType)
                .uploadedBy(uploadedBy)
                .createdAt(Instant.now())
                .build();
        documentVersionRepository.save(version);
        cleanupOldVersions(doc);
    }

    private void cleanupOldVersions(Document doc) {
        User owner = doc.getOwner();
        int retentionDays = owner.getVersionRetentionDays();
        if (retentionDays <= 0) return;

        Instant cutoff = Instant.now().minusSeconds(retentionDays * 86400L);
        List<DocumentVersion> oldVersions = documentVersionRepository.findByDocumentAndCreatedAtBefore(doc, cutoff);

        for (DocumentVersion old : oldVersions) {
            try {
                storageService.delete(old.getStoredFilename());
            } catch (IOException e) {
                log.warn("Failed to delete version file: {}", old.getStoredFilename(), e);
            }
            documentVersionRepository.delete(old);
        }

        if (!oldVersions.isEmpty()) {
            log.info("Cleaned up {} old version(s) for document {}", oldVersions.size(), doc.getId());
        }
    }

    private void checkAccess(Document doc, User user) {
        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canRead = hasPermission(doc, user, DocumentPermission.PermissionType.READ)
                       || hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);
        if (!isOwner && !canRead) {
            throw new AccessDeniedException("Access denied to document " + doc.getId());
        }
    }

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/rtf",
            "text/plain",
            "text/csv",
            "text/markdown",
            "text/html",
            "text/xml",
            "application/json",
            "application/xml",
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "image/bmp"
    );

    private void validateFileType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("File type not allowed: " + contentType);
        }
    }

    private String resolvePermission(Document doc, User user) {
        if (doc.getOwner().getId().equals(user.getId())) return "OWNER";
        if (hasPermission(doc, user, DocumentPermission.PermissionType.WRITE)) return "WRITE";
        if (hasPermission(doc, user, DocumentPermission.PermissionType.READ)) return "READ";
        return null;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "bin";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private boolean hasPermission(Document doc, User user, DocumentPermission.PermissionType type) {
        return doc.getPermissions().stream()
                .anyMatch(p -> p.getUser().getId().equals(user.getId())
                            && p.getPermissionType() == type);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private DocumentResponse toResponse(Document doc, User user) {
        int versionCount = documentVersionRepository.countByDocument(doc);
        boolean isFavorite = favoriteRepository.existsByUserAndDocument(user, doc);
        DocumentResponse.DocumentResponseBuilder builder = DocumentResponse.builder()
                .id(doc.getId())
                .originalFilename(doc.getOriginalFilename())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .description(doc.getDescription())
                .documentType(doc.getDocumentType())
                .ownerUsername(doc.getOwner().getUsername())
                .deletedAt(doc.getDeletedAt())
                .permission(resolvePermission(doc, user))
                .uploadedAt(doc.getUploadedAt())
                .currentVersion(doc.getCurrentVersion())
                .versionCount(versionCount)
                .favorite(isFavorite)
                .tags(doc.getTags().stream().map(Tag::getName).toList())
                .retentionAt(doc.getRetentionAt())
                .legalHold(doc.getLegalHold() != null && doc.getLegalHold());
        if (doc.getFolder() != null) {
            builder.folderId(doc.getFolder().getId());
            builder.folderName(doc.getFolder().getName());
        }
        return builder.build();
    }
}

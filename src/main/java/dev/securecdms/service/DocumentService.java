package dev.securecdms.service;

import dev.securecdms.dto.response.DocumentResponse;
import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentPermission;
import dev.securecdms.model.Folder;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.FolderRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    @Transactional
    public DocumentResponse upload(MultipartFile file, String description, Long folderId, String username) throws IOException {
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

        Document doc = Document.builder()
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFilename)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .description(description)
                .owner(owner)
                .folder(folder)
                .build();

        documentRepository.save(doc);
        log.info("Document uploaded: {} by {}", file.getOriginalFilename(), username);

        auditService.log("UPLOAD", owner.getId(), doc.getId(),
                "Uploaded: " + file.getOriginalFilename(), null);

        return toResponse(doc, owner);
    }

    @Transactional
    public DocumentResponse update(Long documentId, String username, MultipartFile file, String description) throws IOException {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canWrite = hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canWrite) {
            throw new AccessDeniedException("Only the owner or a user with WRITE permission can update this document");
        }

        if (file != null && !file.isEmpty()) {
            validateFileType(file);
            storageService.delete(doc.getStoredFilename());
            String newStoredFilename = storageService.store(file);
            doc.setStoredFilename(newStoredFilename);
            doc.setOriginalFilename(file.getOriginalFilename());
            doc.setContentType(file.getContentType());
            doc.setFileSize(file.getSize());
        }

        if (description != null) {
            doc.setDescription(description);
        }

        documentRepository.save(doc);
        log.info("Document updated: {} by {}", doc.getOriginalFilename(), username);

        auditService.log("UPDATE", user.getId(), documentId,
                "Updated: " + doc.getOriginalFilename(), null);

        return toResponse(doc, user);
    }

    @Transactional
    public DocumentResponse moveToFolder(Long documentId, Long folderId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

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

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listMyDocuments(String username, Pageable pageable) {
        User owner = getUser(username);
        return documentRepository.findByOwner(owner, pageable).map(d -> toResponse(d, owner));
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(String username, String query, Pageable pageable) {
        User user = getUser(username);
        return documentRepository.searchOwnedAndShared(user, query, pageable).map(d -> toResponse(d, user));
    }

    @Transactional(readOnly = true)
    public DownloadResult download(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean canRead = hasPermission(doc, user, DocumentPermission.PermissionType.READ)
                       || hasPermission(doc, user, DocumentPermission.PermissionType.WRITE);

        if (!isOwner && !canRead) {
            throw new AccessDeniedException("Access denied to document " + documentId);
        }

        auditService.log("DOWNLOAD", user.getId(), doc.getId(),
                "Downloaded: " + doc.getOriginalFilename(), null);

        return new DownloadResult(
                storageService.load(doc.getStoredFilename()),
                doc.getOriginalFilename());
    }

    public record DownloadResult(Path path, String originalFilename) {}

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listSharedWithMe(String username, Pageable pageable) {
        User user = getUser(username);
        return documentRepository.findSharedWithUser(user, pageable).map(d -> toResponse(d, user));
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listSharedByMe(String username, Pageable pageable) {
        User user = getUser(username);
        return documentRepository.findSharedByOwner(user, pageable).map(d -> toResponse(d, user));
    }

    @Transactional
    public void delete(Long documentId, String username) throws IOException {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        boolean isOwner = doc.getOwner().getId().equals(user.getId());

        if (isOwner) {
            storageService.delete(doc.getStoredFilename());
            documentRepository.delete(doc);
            auditService.log("DELETE", user.getId(), documentId,
                    "Deleted: " + doc.getOriginalFilename(), null);
            log.info("Document deleted: {} by {}", doc.getOriginalFilename(), username);
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

    // ---- Helpers ----

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "image/png",
            "image/jpeg",
            "image/gif"
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
        DocumentResponse.DocumentResponseBuilder builder = DocumentResponse.builder()
                .id(doc.getId())
                .originalFilename(doc.getOriginalFilename())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .description(doc.getDescription())
                .ownerUsername(doc.getOwner().getUsername())
                .permission(resolvePermission(doc, user))
                .uploadedAt(doc.getUploadedAt());
        if (doc.getFolder() != null) {
            builder.folderId(doc.getFolder().getId());
            builder.folderName(doc.getFolder().getName());
        }
        return builder.build();
    }
}

package dev.securecdms.service;

import dev.securecdms.dto.response.DocumentResponse;
import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    @Transactional
    public DocumentResponse upload(MultipartFile file, String description, String username) throws IOException {
        User owner = getUser(username);

        String storedFilename = storageService.store(file);

        Document doc = Document.builder()
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFilename)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .description(description)
                .owner(owner)
                .build();

        documentRepository.save(doc);
        log.info("Dokument hochgeladen: {} von {}", file.getOriginalFilename(), username);

        auditService.log("UPLOAD", owner.getId(), doc.getId(),
                "Hochgeladen: " + file.getOriginalFilename(), null);

        return toResponse(doc);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listMyDocuments(String username, Pageable pageable) {
        User owner = getUser(username);
        return documentRepository.findByOwner(owner, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(String username, String query, Pageable pageable) {
        User owner = getUser(username);
        return documentRepository.searchByOwner(owner, query, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Path download(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        // Zugriff erlaubt wenn: Owner oder hat Permission
        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        boolean hasPermission = doc.getPermissions().stream()
                .anyMatch(p -> p.getUser().getId().equals(user.getId()));

        if (!isOwner && !hasPermission) {
            throw new AccessDeniedException("Kein Zugriff auf Dokument " + documentId);
        }

        auditService.log("DOWNLOAD", user.getId(), doc.getId(),
                "Heruntergeladen: " + doc.getOriginalFilename(), null);

        return storageService.load(doc.getStoredFilename());
    }

    @Transactional
    public void delete(Long documentId, String username) throws IOException {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        if (!doc.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Nur der Owner kann Dokumente löschen");
        }

        storageService.delete(doc.getStoredFilename());
        documentRepository.delete(doc);

        auditService.log("DELETE", user.getId(), documentId,
                "Gelöscht: " + doc.getOriginalFilename(), null);

        log.info("Dokument gelöscht: {} von {}", doc.getOriginalFilename(), username);
    }

    // ---- Helpers ----

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User nicht gefunden: " + username));
    }

    private Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dokument nicht gefunden: " + id));
    }

    private DocumentResponse toResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .originalFilename(doc.getOriginalFilename())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .description(doc.getDescription())
                .ownerUsername(doc.getOwner().getUsername())
                .uploadedAt(doc.getUploadedAt())
                .build();
    }
}
package dev.securecdms.service;

import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentPermission;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public void grantPermission(Long documentId, String ownerUsername,
                                String targetUsername, DocumentPermission.PermissionType type) {

        Document doc = getDocument(documentId);
        User owner = getUser(ownerUsername);
        User target = getUser(targetUsername);

        if (!doc.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Only the owner can grant permissions");
        }

        if (owner.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Owner cannot grant permissions to themselves");
        }

        doc.getPermissions().stream()
                .filter(p -> p.getUser().getId().equals(target.getId()))
                .findFirst()
                .ifPresentOrElse(
                        p -> p.setPermissionType(type),
                        () -> doc.getPermissions().add(
                                DocumentPermission.builder()
                                        .document(doc)
                                        .user(target)
                                        .permissionType(type)
                                        .build()
                        )
                );

        documentRepository.save(doc);
        log.info("Permission {} granted to {} on document {}", type, targetUsername, documentId);

        auditService.log("PERMISSION_GRANT", owner.getId(), documentId,
                type + " for " + targetUsername, null);
    }

    @Transactional
    public void revokePermission(Long documentId, String ownerUsername, String targetUsername) {
        Document doc = getDocument(documentId);
        User owner = getUser(ownerUsername);
        User target = getUser(targetUsername);

        if (!doc.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Only the owner can revoke permissions");
        }

        boolean removed = doc.getPermissions()
                .removeIf(p -> p.getUser().getId().equals(target.getId()));

        if (!removed) {
            throw new ResourceNotFoundException("No permission found for " + targetUsername);
        }

        documentRepository.save(doc);
        log.info("Permission revoked for {} on document {}", targetUsername, documentId);

        auditService.log("PERMISSION_REVOKE", owner.getId(), documentId,
                "Revoked for " + targetUsername, null);
    }

    @Transactional(readOnly = true)
    public List<PermissionInfo> listPermissions(Long documentId, String ownerUsername) {
        Document doc = getDocument(documentId);
        User owner = getUser(ownerUsername);

        if (!doc.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Only the owner can view permissions");
        }

        return doc.getPermissions().stream()
                .map(p -> new PermissionInfo(
                        p.getUser().getUsername(),
                        p.getPermissionType(),
                        p.getGrantedAt()))
                .toList();
    }

    public record PermissionInfo(
            String username,
            DocumentPermission.PermissionType permissionType,
            java.time.Instant grantedAt) {}

    private Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}

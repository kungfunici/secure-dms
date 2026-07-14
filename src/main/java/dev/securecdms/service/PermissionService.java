package dev.securecdms.service;

import dev.securecdms.dto.request.ShareRequest;
import dev.securecdms.dto.response.PermissionResponse;
import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentPermission;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final NotificationService notificationService;

    @Transactional
    public PermissionResponse grant(Long documentId, ShareRequest request, String ownerUsername) {
        Document doc = getDocument(documentId);
        User owner = getUser(ownerUsername);

        if (!doc.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Only the owner can share this document");
        }

        User targetUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + request.getUserId()));

        if (targetUser.getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Cannot share document with yourself");
        }

        DocumentPermission.PermissionType type = DocumentPermission.PermissionType.valueOf(request.getPermissionType());

        boolean alreadyExists = doc.getPermissions().stream()
                .anyMatch(p -> p.getUser().getId().equals(targetUser.getId())
                            && p.getPermissionType() == type);
        if (alreadyExists) {
            throw new IllegalArgumentException("User already has this permission");
        }

        DocumentPermission perm = DocumentPermission.builder()
                .document(doc)
                .user(targetUser)
                .permissionType(type)
                .build();

        doc.getPermissions().add(perm);
        documentRepository.save(doc);

        auditService.log("SHARE", owner.getId(), documentId,
                "Shared " + type + " access with " + targetUser.getUsername(), null);

        notificationService.create(targetUser.getId(), "SHARE",
                doc.getOriginalFilename(),
                owner.getUsername() + " gave you " + type + " access",
                documentId);

        log.info("Shared {} access to document {} with user {}", type, documentId, targetUser.getUsername());
        return toResponse(perm);
    }

    @Transactional
    public void revoke(Long documentId, Long userId, String ownerUsername) {
        Document doc = getDocument(documentId);
        User owner = getUser(ownerUsername);

        if (!doc.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Only the owner can revoke permissions");
        }

        boolean removed = doc.getPermissions().removeIf(p -> p.getUser().getId().equals(userId));
        if (!removed) {
            throw new ResourceNotFoundException("No permission found for user " + userId);
        }
        documentRepository.save(doc);

        auditService.log("REVOKE", owner.getId(), documentId,
                "Revoked access from user " + userId, null);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> list(Long documentId, String username) {
        Document doc = getDocument(documentId);
        User user = getUser(username);

        boolean isOwner = doc.getOwner().getId().equals(user.getId());
        if (!isOwner) {
            throw new AccessDeniedException("Only the owner can view permissions");
        }

        return doc.getPermissions().stream()
                .map(this::toResponse)
                .toList();
    }

    private Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found: " + username));
    }

    private PermissionResponse toResponse(DocumentPermission perm) {
        return PermissionResponse.builder()
                .id(perm.getId())
                .userId(perm.getUser().getId())
                .username(perm.getUser().getUsername())
                .permissionType(perm.getPermissionType().name())
                .grantedAt(perm.getGrantedAt())
                .build();
    }
}

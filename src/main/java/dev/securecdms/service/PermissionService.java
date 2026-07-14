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

        // Nur der Owner darf Permissions vergeben
        if (!doc.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Nur der Owner kann Berechtigungen vergeben");
        }

        // Sich selbst berechtigen macht keinen Sinn
        if (owner.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Owner kann sich nicht selbst berechtigen");
        }

        // Existiert schon eine Permission? → updaten
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
        log.info("Permission {} für {} auf Dokument {} vergeben", type, targetUsername, documentId);

        auditService.log("PERMISSION_GRANT", owner.getId(), documentId,
                type + " für " + targetUsername, null);
    }

    @Transactional
    public void revokePermission(Long documentId, String ownerUsername, String targetUsername) {
        Document doc = getDocument(documentId);
        User owner = getUser(ownerUsername);
        User target = getUser(targetUsername);

        if (!doc.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Nur der Owner kann Berechtigungen entziehen");
        }

        boolean removed = doc.getPermissions()
                .removeIf(p -> p.getUser().getId().equals(target.getId()));

        if (!removed) {
            throw new ResourceNotFoundException("Keine Permission für " + targetUsername + " gefunden");
        }

        documentRepository.save(doc);
        log.info("Permission für {} auf Dokument {} entzogen", targetUsername, documentId);

        auditService.log("PERMISSION_REVOKE", owner.getId(), documentId,
                "Entzogen für " + targetUsername, null);
    }

    @Transactional(readOnly = true)
    public List<PermissionInfo> listPermissions(Long documentId, String ownerUsername) {
        Document doc = getDocument(documentId);
        User owner = getUser(ownerUsername);

        if (!doc.getOwner().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Nur der Owner kann Berechtigungen einsehen");
        }

        return doc.getPermissions().stream()
                .map(p -> new PermissionInfo(
                        p.getUser().getUsername(),
                        p.getPermissionType(),
                        p.getGrantedAt()))
                .toList();
    }

    // Kleines Record als Antwort-Objekt — kein extra DTO nötig
    public record PermissionInfo(
            String username,
            DocumentPermission.PermissionType permissionType,
            java.time.Instant grantedAt) {}

    private Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dokument nicht gefunden: " + id));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User nicht gefunden: " + username));
    }
}

package dev.securecdms.service;

import dev.securecdms.dto.request.LegalHoldRequest;
import dev.securecdms.dto.request.RetentionPolicyRequest;
import dev.securecdms.dto.response.LegalHoldResponse;
import dev.securecdms.dto.response.RetentionPolicyResponse;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.*;
import dev.securecdms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final LegalHoldRepository legalHoldRepository;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final StorageService storageService;

    // ---- Retention Policy CRUD ----

    public Page<RetentionPolicyResponse> getPolicies(Pageable pageable) {
        return retentionPolicyRepository.findAll(pageable).map(this::toPolicyResponse);
    }

    @Transactional
    public RetentionPolicyResponse createPolicy(RetentionPolicyRequest req) {
        RetentionPolicy policy = RetentionPolicy.builder()
                .name(req.getName())
                .documentType(req.getDocumentType())
                .retentionDays(req.getRetentionDays())
                .action(req.getAction() != null ? req.getAction() : "DELETE")
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .build();
        if (req.getFolderId() != null) {
            policy.setFolder(folderRepository.findById(req.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + req.getFolderId())));
        }
        return toPolicyResponse(retentionPolicyRepository.save(policy));
    }

    @Transactional
    public RetentionPolicyResponse updatePolicy(Long id, RetentionPolicyRequest req) {
        RetentionPolicy policy = retentionPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RetentionPolicy not found: " + id));
        policy.setName(req.getName());
        policy.setDocumentType(req.getDocumentType());
        policy.setRetentionDays(req.getRetentionDays());
        policy.setAction(req.getAction() != null ? req.getAction() : policy.getAction());
        policy.setEnabled(req.getEnabled() != null ? req.getEnabled() : policy.getEnabled());
        if (req.getFolderId() != null) {
            policy.setFolder(folderRepository.findById(req.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + req.getFolderId())));
        } else {
            policy.setFolder(null);
        }
        return toPolicyResponse(retentionPolicyRepository.save(policy));
    }

    @Transactional
    public void deletePolicy(Long id) {
        RetentionPolicy policy = retentionPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RetentionPolicy not found: " + id));
        retentionPolicyRepository.delete(policy);
    }

    // ---- Legal Hold CRUD ----

    public Page<LegalHoldResponse> getLegalHolds(Pageable pageable) {
        return legalHoldRepository.findAll(pageable).map(this::toLegalHoldResponse);
    }

    @Transactional
    public LegalHoldResponse createLegalHold(LegalHoldRequest req, String username) {
        Document doc = documentRepository.findById(req.getDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + req.getDocumentId()));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        legalHoldRepository.findByDocumentAndReleasedAtIsNull(doc).ifPresent(existing -> {
            throw new IllegalStateException("Document already has an active legal hold");
        });

        LegalHold hold = LegalHold.builder()
                .document(doc)
                .reason(req.getReason())
                .createdBy(user)
                .build();
        LegalHold saved = legalHoldRepository.save(hold);

        doc.setLegalHold(true);
        documentRepository.save(doc);

        auditService.log("LEGAL_HOLD_CREATED", user.getId(), doc.getId(),
                "Legal hold placed: " + req.getReason(), null);
        return toLegalHoldResponse(saved);
    }

    @Transactional
    public LegalHoldResponse releaseLegalHold(Long id) {
        LegalHold hold = legalHoldRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LegalHold not found: " + id));
        hold.setReleasedAt(Instant.now());
        LegalHold saved = legalHoldRepository.save(hold);

        Document doc = hold.getDocument();
        boolean hasOtherHolds = legalHoldRepository.existsByDocumentAndReleasedAtIsNull(doc);
        if (!hasOtherHolds) {
            doc.setLegalHold(false);
            documentRepository.save(doc);
        }

        auditService.log("LEGAL_HOLD_RELEASED", null, doc.getId(),
                "Legal hold released: " + hold.getReason(), null);
        return toLegalHoldResponse(saved);
    }

    @Transactional
    public void applyPolicy(Document doc) {
        List<RetentionPolicy> policies = retentionPolicyRepository.findByEnabledTrue();
        for (RetentionPolicy policy : policies) {
            if (matches(doc, policy)) {
                Instant retentionAt = Instant.now().plusSeconds(policy.getRetentionDays() * 86400L);
                doc.setRetentionAt(retentionAt);
                documentRepository.save(doc);
                log.info("Applied retention policy '{}' to document {}: retention at {}",
                        policy.getName(), doc.getId(), retentionAt);
                return;
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void processExpiredDocuments() {
        log.info("Running retention job...");
        List<Document> expired = documentRepository.findByRetentionAtBeforeAndDeletedAtIsNull(Instant.now());
        int deleted = 0, archived = 0;
        for (Document doc : expired) {
            if (doc.getLegalHold()) {
                log.info("Skipping document {} due to active legal hold", doc.getId());
                continue;
            }
            RetentionPolicy policy = findMatchingPolicy(doc);
            if (policy != null && "ARCHIVE".equals(policy.getAction())) {
                doc.setDeletedAt(Instant.now());
                documentRepository.save(doc);
                archived++;
            } else {
                try {
                    storageService.delete(doc.getStoredFilename());
                } catch (Exception e) {
                    log.warn("Failed to delete storage for document {}: {}", doc.getId(), e.getMessage());
                }
                documentRepository.delete(doc);
                deleted++;
            }
        }
        if (deleted > 0 || archived > 0) {
            log.info("Retention job: deleted {} documents, archived {} documents", deleted, archived);
        }
    }

    private boolean matches(Document doc, RetentionPolicy policy) {
        if (policy.getDocumentType() != null && !policy.getDocumentType().equals(doc.getDocumentType())) {
            return false;
        }
        if (policy.getFolder() != null) {
            if (doc.getFolder() == null || !policy.getFolder().getId().equals(doc.getFolder().getId())) {
                return false;
            }
        }
        return true;
    }

    private RetentionPolicy findMatchingPolicy(Document doc) {
        return retentionPolicyRepository.findByEnabledTrue().stream()
                .filter(p -> matches(doc, p))
                .findFirst()
                .orElse(null);
    }

    private RetentionPolicyResponse toPolicyResponse(RetentionPolicy p) {
        return RetentionPolicyResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .documentType(p.getDocumentType())
                .folderId(p.getFolder() != null ? p.getFolder().getId() : null)
                .folderName(p.getFolder() != null ? p.getFolder().getName() : null)
                .retentionDays(p.getRetentionDays())
                .action(p.getAction())
                .enabled(p.getEnabled())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private LegalHoldResponse toLegalHoldResponse(LegalHold h) {
        return LegalHoldResponse.builder()
                .id(h.getId())
                .documentId(h.getDocument().getId())
                .documentName(h.getDocument().getOriginalFilename())
                .reason(h.getReason())
                .createdByUsername(h.getCreatedBy().getUsername())
                .createdAt(h.getCreatedAt())
                .releasedAt(h.getReleasedAt())
                .build();
    }
}

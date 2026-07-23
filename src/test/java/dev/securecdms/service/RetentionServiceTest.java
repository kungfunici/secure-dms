package dev.securecdms.service;

import dev.securecdms.dto.request.LegalHoldRequest;
import dev.securecdms.dto.request.RetentionPolicyRequest;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.*;
import dev.securecdms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock private RetentionPolicyRepository retentionPolicyRepository;
    @Mock private LegalHoldRepository legalHoldRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private StorageService storageService;

    private RetentionService retentionService;
    private Document doc;
    private User admin;

    @BeforeEach
    void setUp() {
        retentionService = new RetentionService(
                retentionPolicyRepository, legalHoldRepository, documentRepository,
                folderRepository, userRepository, auditService, storageService);

        admin = User.builder().id(1L).username("admin").role(Role.ROLE_ADMIN).build();
        doc = Document.builder()
                .id(10L)
                .originalFilename("test.pdf")
                .documentType("PDF")
                .owner(admin)
                .build();
    }

    @Test
    void getPolicies_shouldReturnPage() {
        var policy = RetentionPolicy.builder().id(1L).name("Test").retentionDays(90).build();
        when(retentionPolicyRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(policy)));

        var result = retentionService.getPolicies(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("Test", result.getContent().get(0).getName());
    }

    @Test
    void createPolicy_shouldSaveAndReturn() {
        var req = new RetentionPolicyRequest();
        req.setName("Delete PDFs");
        req.setDocumentType("PDF");
        req.setRetentionDays(90);
        req.setAction("DELETE");
        req.setEnabled(true);

        when(retentionPolicyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = retentionService.createPolicy(req);

        assertEquals("Delete PDFs", result.getName());
        assertEquals("PDF", result.getDocumentType());
        assertEquals(90, result.getRetentionDays());
        assertEquals("DELETE", result.getAction());
        assertTrue(result.getEnabled());
        verify(retentionPolicyRepository).save(any());
    }

    @Test
    void createPolicy_withFolder_shouldLinkFolder() {
        Folder folder = Folder.builder().id(5L).name("Invoices").build();
        when(folderRepository.findById(5L)).thenReturn(Optional.of(folder));
        when(retentionPolicyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new RetentionPolicyRequest();
        req.setName("Invoices 1 year");
        req.setFolderId(5L);
        req.setRetentionDays(365);

        var result = retentionService.createPolicy(req);

        assertEquals(5L, result.getFolderId());
        assertEquals("Invoices", result.getFolderName());
    }

    @Test
    void updatePolicy_shouldModifyExisting() {
        var policy = RetentionPolicy.builder().id(1L).name("Old").retentionDays(30).action("DELETE").enabled(true).build();
        when(retentionPolicyRepository.findById(1L)).thenReturn(Optional.of(policy));
        when(retentionPolicyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new RetentionPolicyRequest();
        req.setName("Updated");
        req.setDocumentType("PDF");
        req.setRetentionDays(60);
        req.setAction("ARCHIVE");
        req.setEnabled(false);

        var result = retentionService.updatePolicy(1L, req);

        assertEquals("Updated", result.getName());
        assertEquals(60, result.getRetentionDays());
        assertEquals("ARCHIVE", result.getAction());
        assertFalse(result.getEnabled());
    }

    @Test
    void deletePolicy_shouldRemove() {
        var policy = RetentionPolicy.builder().id(1L).name("Test").retentionDays(30).build();
        when(retentionPolicyRepository.findById(1L)).thenReturn(Optional.of(policy));

        retentionService.deletePolicy(1L);

        verify(retentionPolicyRepository).delete(policy);
    }

    @Test
    void deletePolicy_shouldThrowWhenNotFound() {
        when(retentionPolicyRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> retentionService.deletePolicy(99L));
    }

    @Test
    void getLegalHolds_shouldReturnPage() {
        var hold = LegalHold.builder().id(1L).document(doc).reason("Litigation").createdBy(admin).build();
        when(legalHoldRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(hold)));

        var result = retentionService.getLegalHolds(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("Litigation", result.getContent().get(0).getReason());
    }

    @Test
    void createLegalHold_shouldSaveAndSetFlag() {
        when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(legalHoldRepository.findByDocumentAndReleasedAtIsNull(doc)).thenReturn(Optional.empty());
        when(legalHoldRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var req = new LegalHoldRequest();
        req.setDocumentId(10L);
        req.setReason("Court order");

        var result = retentionService.createLegalHold(req, "admin");

        assertEquals("Court order", result.getReason());
        assertTrue(doc.getLegalHold());
        verify(documentRepository).save(doc);
        verify(auditService).log(eq("LEGAL_HOLD_CREATED"), any(), eq(10L), any(), any());
    }

    @Test
    void createLegalHold_shouldThrowIfAlreadyActive() {
        when(documentRepository.findById(10L)).thenReturn(Optional.of(doc));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(legalHoldRepository.findByDocumentAndReleasedAtIsNull(doc))
                .thenReturn(Optional.of(LegalHold.builder().id(1L).build()));

        var req = new LegalHoldRequest();
        req.setDocumentId(10L);
        req.setReason("Another hold");

        assertThrows(IllegalStateException.class, () -> retentionService.createLegalHold(req, "admin"));
    }

    @Test
    void releaseLegalHold_shouldClearFlag() {
        doc.setLegalHold(true);
        var hold = LegalHold.builder().id(1L).document(doc).reason("Litigation").createdBy(admin).build();
        when(legalHoldRepository.findById(1L)).thenReturn(Optional.of(hold));
        when(legalHoldRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(legalHoldRepository.existsByDocumentAndReleasedAtIsNull(doc)).thenReturn(false);

        var result = retentionService.releaseLegalHold(1L);

        assertNotNull(result.getReleasedAt());
        assertFalse(doc.getLegalHold());
        verify(documentRepository).save(doc);
        verify(auditService).log(eq("LEGAL_HOLD_RELEASED"), any(), eq(10L), any(), any());
    }

    @Test
    void releaseLegalHold_shouldKeepFlagIfOtherHoldsExist() {
        doc.setLegalHold(true);
        var hold = LegalHold.builder().id(1L).document(doc).reason("Litigation").createdBy(admin).build();
        when(legalHoldRepository.findById(1L)).thenReturn(Optional.of(hold));
        when(legalHoldRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(legalHoldRepository.existsByDocumentAndReleasedAtIsNull(doc)).thenReturn(true);

        retentionService.releaseLegalHold(1L);

        assertTrue(doc.getLegalHold());
    }

    @Test
    void applyPolicy_shouldSetRetentionAt() {
        var policy = RetentionPolicy.builder().id(1L).name("PDF 90d").documentType("PDF").retentionDays(90).action("DELETE").enabled(true).build();
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of(policy));

        retentionService.applyPolicy(doc);

        assertNotNull(doc.getRetentionAt());
        verify(documentRepository).save(doc);
    }

    @Test
    void applyPolicy_shouldNotMatchDifferentType() {
        var policy = RetentionPolicy.builder().id(1L).name("PDF 90d").documentType("Invoice").retentionDays(90).action("DELETE").enabled(true).build();
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of(policy));

        retentionService.applyPolicy(doc);

        assertNull(doc.getRetentionAt());
        verify(documentRepository, never()).save(doc);
    }

    @Test
    void applyPolicy_shouldMatchByFolder() {
        Folder folder = Folder.builder().id(5L).name("Invoices").build();
        doc.setFolder(folder);
        var policy = RetentionPolicy.builder().id(1L).name("Folder policy").folder(folder).retentionDays(30).action("DELETE").enabled(true).build();
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of(policy));

        retentionService.applyPolicy(doc);

        assertNotNull(doc.getRetentionAt());
    }

    @Test
    void applyPolicy_shouldNotMatchDifferentFolder() {
        Folder docFolder = Folder.builder().id(5L).name("Invoices").build();
        Folder policyFolder = Folder.builder().id(6L).name("Contracts").build();
        doc.setFolder(docFolder);
        var policy = RetentionPolicy.builder().id(1L).name("Folder policy").folder(policyFolder).retentionDays(30).action("DELETE").enabled(true).build();
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of(policy));

        retentionService.applyPolicy(doc);

        assertNull(doc.getRetentionAt());
    }

    @Test
    void processExpiredDocuments_shouldDeleteExpiredDocs() {
        doc.setRetentionAt(Instant.now().minusSeconds(3600));
        when(documentRepository.findByRetentionAtBeforeAndDeletedAtIsNull(any())).thenReturn(List.of(doc));
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of());

        retentionService.processExpiredDocuments();

        verify(documentRepository).delete(doc);
    }

    @Test
    void processExpiredDocuments_shouldArchiveWhenActionIsArchive() {
        doc.setRetentionAt(Instant.now().minusSeconds(3600));
        var policy = RetentionPolicy.builder().id(1L).name("Archive").documentType("PDF").retentionDays(1).action("ARCHIVE").enabled(true).build();
        when(documentRepository.findByRetentionAtBeforeAndDeletedAtIsNull(any())).thenReturn(List.of(doc));
        when(retentionPolicyRepository.findByEnabledTrue()).thenReturn(List.of(policy));

        retentionService.processExpiredDocuments();

        assertNotNull(doc.getDeletedAt());
        verify(documentRepository).save(doc);
        verify(documentRepository, never()).delete(any());
    }

    @Test
    void processExpiredDocuments_shouldSkipLegalHoldDocs() {
        doc.setLegalHold(true);
        doc.setRetentionAt(Instant.now().minusSeconds(3600));
        when(documentRepository.findByRetentionAtBeforeAndDeletedAtIsNull(any())).thenReturn(List.of(doc));

        retentionService.processExpiredDocuments();

        verify(documentRepository, never()).delete(any());
        verify(documentRepository, never()).save(any());
    }
}

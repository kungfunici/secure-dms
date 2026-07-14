package dev.securecdms.service;

import dev.securecdms.dto.request.ShareRequest;
import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentPermission;
import dev.securecdms.model.Role;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private PermissionService permissionService;
    private User owner;
    private User targetUser;
    private Document document;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(documentRepository, userRepository, auditService);

        owner = User.builder().id(1L).username("owner").role(Role.ROLE_USER).build();
        targetUser = User.builder().id(2L).username("target").role(Role.ROLE_USER).build();

        document = Document.builder()
                .id(1L).owner(owner).permissions(new ArrayList<>()).build();
    }

    private ShareRequest shareReq(String username, String type) {
        ShareRequest r = new ShareRequest();
        r.setUsername(username);
        r.setPermissionType(type);
        return r;
    }

    @Test
    void grant_shouldAddNewPermission() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(userRepository.findByUsername("target")).thenReturn(Optional.of(targetUser));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        permissionService.grant(1L, shareReq("target", "READ"), "owner");

        assertEquals(1, document.getPermissions().size());
        assertEquals(DocumentPermission.PermissionType.READ, document.getPermissions().getFirst().getPermissionType());
        verify(auditService).log(eq("SHARE"), eq(1L), eq(1L), any(), eq(null));
    }

    @Test
    void grant_shouldDenyNonOwner() {
        User stranger = User.builder().id(3L).username("stranger").build();
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.of(stranger));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class,
                () -> permissionService.grant(1L, shareReq("target", "READ"), "stranger"));
    }

    @Test
    void grant_shouldDenySelfGrant() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(IllegalArgumentException.class,
                () -> permissionService.grant(1L, shareReq("owner", "READ"), "owner"));
    }

    @Test
    void revoke_shouldRemovePermission() {
        DocumentPermission existing = DocumentPermission.builder()
                .document(document).user(targetUser).permissionType(DocumentPermission.PermissionType.READ).build();
        document.getPermissions().add(existing);

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        permissionService.revoke(1L, 2L, "owner");

        assertTrue(document.getPermissions().isEmpty());
        verify(auditService).log(eq("REVOKE"), eq(1L), eq(1L), any(), eq(null));
    }

    @Test
    void revoke_shouldThrowWhenNoPermission() {
        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(ResourceNotFoundException.class,
                () -> permissionService.revoke(1L, 2L, "owner"));
    }

    @Test
    void list_shouldReturnPermissions() {
        DocumentPermission existing = DocumentPermission.builder()
                .document(document).user(targetUser).permissionType(DocumentPermission.PermissionType.READ).build();
        document.getPermissions().add(existing);

        when(userRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        var permissions = permissionService.list(1L, "owner");

        assertEquals(1, permissions.size());
        assertEquals("target", permissions.getFirst().getUsername());
    }

    @Test
    void list_shouldDenyNonOwner() {
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.of(
                User.builder().id(3L).username("stranger").build()));
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

        assertThrows(AccessDeniedException.class,
                () -> permissionService.list(1L, "stranger"));
    }
}

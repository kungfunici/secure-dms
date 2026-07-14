package dev.securecdms.service;

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
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private StorageService storageService;
    @Mock private AuditService auditService;

    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, documentRepository, storageService, auditService);
        user = User.builder().id(1L).username("testuser").email("test@example.com").role(Role.ROLE_USER).build();
    }

    @Test
    void getProfile_shouldReturnUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var result = userService.getProfile(1L);

        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void getProfile_shouldThrowForNonExistent() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getProfile(99L));
    }

    @Test
    void updateProfile_shouldSetAvatar() throws IOException {
        var avatar = new MockMultipartFile("avatar", "pic.jpg", "image/jpeg", "data".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(storageService.storeWithName(any(), anyString())).thenReturn("profile-1-test.jpg");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = userService.updateProfile(1L, "testuser", avatar);

        assertNotNull(result.getProfilePicture());
        verify(storageService).storeWithName(any(), anyString());
    }

    @Test
    void updateProfile_shouldReplaceOldAvatar() throws IOException {
        user.setProfilePicture("old-avatar.jpg");
        var avatar = new MockMultipartFile("avatar", "new.jpg", "image/jpeg", "data".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(storageService.storeWithName(any(), anyString())).thenReturn("profile-1-new.jpg");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        userService.updateProfile(1L, "testuser", avatar);

        verify(storageService).delete("old-avatar.jpg");
    }

    @Test
    void updateProfile_shouldDenyOtherUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(AccessDeniedException.class, () -> userService.updateProfile(1L, "other", null));
    }

    @Test
    void deleteAccount_shouldDeleteUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(documentRepository.findSharedWithUserList(user)).thenReturn(List.of());

        userService.deleteAccount(1L, "testuser");

        verify(userRepository).delete(user);
    }

    @Test
    void deleteAccount_shouldDenyOtherUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(AccessDeniedException.class, () -> userService.deleteAccount(1L, "other"));
    }

    @Test
    void deleteAccount_shouldRemovePermissions() {
        Document sharedDoc = Document.builder().id(2L).owner(User.builder().id(2L).build())
                .permissions(new ArrayList<>()).build();
        sharedDoc.getPermissions().add(
                DocumentPermission.builder().user(user).build());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(documentRepository.findSharedWithUserList(user)).thenReturn(List.of(sharedDoc));

        userService.deleteAccount(1L, "testuser");

        assertTrue(sharedDoc.getPermissions().isEmpty());
        verify(documentRepository).saveAll(anyList());
        verify(userRepository).delete(user);
    }

    @Test
    void searchUsers_shouldReturnMatchingUsers() {
        when(userRepository.searchByUsername("test")).thenReturn(List.of(user));

        var results = userService.searchUsers("test");

        assertEquals(1, results.size());
        assertEquals("testuser", results.getFirst().getUsername());
    }

    @Test
    void searchUsers_shouldReturnEmptyForNoMatch() {
        when(userRepository.searchByUsername("nobody")).thenReturn(List.of());

        var results = userService.searchUsers("nobody");

        assertTrue(results.isEmpty());
    }
}

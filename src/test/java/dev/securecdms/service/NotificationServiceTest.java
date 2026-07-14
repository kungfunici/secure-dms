package dev.securecdms.service;

import dev.securecdms.model.Notification;
import dev.securecdms.model.Role;
import dev.securecdms.model.User;
import dev.securecdms.repository.NotificationRepository;
import dev.securecdms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    private NotificationService notificationService;
    private User user;
    private Notification notification;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository);

        user = User.builder().id(1L).username("testuser").role(Role.ROLE_USER).build();

        notification = Notification.builder()
                .id(1L).user(user).type("SHARE").title("doc.pdf")
                .message("owner gave you WRITE access").documentId(10L)
                .read(false).createdAt(Instant.now()).build();
    }

    @Test
    void create_shouldSaveNotification() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        notificationService.create(1L, "SHARE", "doc.pdf", "owner gave you WRITE access", 10L);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void listNotifications_shouldReturnOrdered() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(notification));

        var results = notificationService.listNotifications("testuser");

        assertEquals(1, results.size());
        assertEquals("doc.pdf", results.getFirst().getTitle());
    }

    @Test
    void listNotifications_shouldReturnEmptyForNoNotifications() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());

        var results = notificationService.listNotifications("testuser");

        assertTrue(results.isEmpty());
    }

    @Test
    void unreadCount_shouldReturnCorrectCount() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(notificationRepository.countByUserAndReadFalse(user)).thenReturn(3L);

        long count = notificationService.unreadCount("testuser");

        assertEquals(3L, count);
    }

    @Test
    void unreadCount_shouldReturnZero() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(notificationRepository.countByUserAndReadFalse(user)).thenReturn(0L);

        long count = notificationService.unreadCount("testuser");

        assertEquals(0L, count);
    }

    @Test
    void markRead_shouldSetReadToTrue() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        notificationService.markRead(1L, "testuser");

        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_shouldDenyOtherUser() {
        User other = User.builder().id(2L).username("other").build();
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(other));
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));

        assertThrows(RuntimeException.class, () -> notificationService.markRead(1L, "other"));
    }

    @Test
    void markAllRead_shouldMarkAllAsRead() {
        Notification n2 = Notification.builder().id(2L).user(user).type("SHARE").title("other.pdf")
                .read(false).createdAt(Instant.now()).build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(notificationRepository.findByUserAndReadFalseOrderByCreatedAtDesc(user))
                .thenReturn(List.of(notification, n2));

        notificationService.markAllRead("testuser");

        assertTrue(notification.isRead());
        assertTrue(n2.isRead());
        verify(notificationRepository).saveAll(anyList());
    }
}

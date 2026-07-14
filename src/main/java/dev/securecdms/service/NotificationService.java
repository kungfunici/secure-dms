package dev.securecdms.service;

import dev.securecdms.dto.response.NotificationResponse;
import dev.securecdms.model.Notification;
import dev.securecdms.model.User;
import dev.securecdms.repository.NotificationRepository;
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
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void create(Long userId, String type, String title, String message, Long documentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));

        Notification notif = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .documentId(documentId)
                .build();

        notificationRepository.save(notif);
        log.debug("Notification created for user {}: {}", userId, title);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listNotifications(String username) {
        User user = getUser(username);
        return notificationRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        User user = getUser(username);
        return notificationRepository.countByUserAndReadFalse(user);
    }

    @Transactional
    public void markRead(Long notificationId, String username) {
        User user = getUser(username);
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        if (!notif.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        notif.setRead(true);
        notificationRepository.save(notif);
    }

    @Transactional
    public void markAllRead(String username) {
        User user = getUser(username);
        List<Notification> unread = notificationRepository.findByUserAndReadFalseOrderByCreatedAtDesc(user);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public void deleteNotification(Long notificationId, String username) {
        User user = getUser(username);
        Notification notif = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        if (!notif.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }
        notificationRepository.delete(notif);
    }

    @Transactional
    public void clearAllNotifications(String username) {
        User user = getUser(username);
        notificationRepository.deleteAllByUser(user);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .documentId(n.getDocumentId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

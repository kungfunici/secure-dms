package dev.securecdms.service;

import dev.securecdms.model.AuditLog;
import dev.securecdms.model.User;
import dev.securecdms.repository.AuditLogRepository;
import dev.securecdms.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;

    @Test
    void log_shouldSaveEntry() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        User user = User.builder().id(1L).username("testuser").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        auditService.log("TEST_ACTION", 1L, 10L, "Test details", "127.0.0.1");

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void getAllLogs_shouldReturnLogs() {
        AuditService auditService = new AuditService(auditLogRepository, userRepository);
        when(auditLogRepository.findAllByOrderByTimestampDesc(any()))
                .thenReturn(new PageImpl<>(List.of(
                        AuditLog.builder().id(1L).action("LOGIN").build())));

        var result = auditService.getAllLogs(PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
        assertEquals("LOGIN", result.getContent().getFirst().getAction());
    }
}

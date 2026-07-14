package dev.securecdms.controller;

import dev.securecdms.dto.response.NotificationResponse;
import dev.securecdms.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private NotificationService notificationService;

    private final NotificationResponse notifResponse = NotificationResponse.builder()
            .id(1L).type("SHARE").title("doc.pdf")
            .message("owner gave you WRITE access").documentId(10L)
            .read(false).createdAt(Instant.now()).build();

    @Test
    @WithMockUser
    void list_shouldReturnNotifications() throws Exception {
        when(notificationService.listNotifications("user")).thenReturn(List.of(notifResponse));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("doc.pdf"))
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    @WithMockUser
    void unreadCount_shouldReturnCount() throws Exception {
        when(notificationService.unreadCount("user")).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    @WithMockUser
    void markRead_shouldReturnOk() throws Exception {
        doNothing().when(notificationService).markRead(1L, "user");

        mockMvc.perform(put("/api/notifications/1/read"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void markAllRead_shouldReturnOk() throws Exception {
        doNothing().when(notificationService).markAllRead("user");

        mockMvc.perform(put("/api/notifications/read-all"))
                .andExpect(status().isOk());
    }
}

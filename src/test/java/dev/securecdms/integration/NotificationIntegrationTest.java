package dev.securecdms.integration;

import dev.securecdms.SecureDmsApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecureDmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String token;
    private String otherToken;
    private Long otherUserId;

    @BeforeEach
    void setUp() throws Exception {
        var res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"notifowner\",\"email\":\"notowner@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        token = res.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
        Long ownerId = Long.parseLong(res.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        var otherRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"notifother\",\"email\":\"notother@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        otherToken = otherRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
        otherUserId = Long.parseLong(otherRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        MockMultipartFile file = new MockMultipartFile("file", "notif.txt", "text/plain", "Notif content".getBytes());
        var uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file).param("description", "notif test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Long docId = Long.parseLong(uploadRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(post("/api/documents/" + docId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUserId + ",\"permissionType\":\"READ\"}")
                        .header("Authorization", "Bearer " + token));
    }

    @Test
    void listNotifications_shouldReturnNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message", containsString("READ access")));
    }

    @Test
    void unreadCount_shouldReturnCount() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());
    }

    @Test
    void markRead_shouldSetReadToTrue() throws Exception {
        var listRes = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + otherToken)).andReturn();
        String listJson = listRes.getResponse().getContentAsString();
        Long notifId = Long.parseLong(listJson.split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(put("/api/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void markAllRead_shouldSetAllToRead() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void deleteNotification_shouldRemove() throws Exception {
        var listRes = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + otherToken)).andReturn();
        String listJson = listRes.getResponse().getContentAsString();
        Long notifId = Long.parseLong(listJson.split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(delete("/api/notifications/" + notifId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void clearAll_shouldRemoveAll() throws Exception {
        mockMvc.perform(delete("/api/notifications/clear-all")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}

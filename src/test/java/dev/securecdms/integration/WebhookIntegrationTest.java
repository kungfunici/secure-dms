package dev.securecdms.integration;

import dev.securecdms.SecureDmsApplication;
import dev.securecdms.model.Role;
import dev.securecdms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class WebhookIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"webhookadmin\",\"email\":\"whadmin@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk());

        // Promote to ADMIN directly in DB
        userRepository.findByUsername("webhookadmin").ifPresent(u -> {
            u.setRole(Role.ROLE_ADMIN);
            userRepository.saveAndFlush(u);
        });

        var adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"webhookadmin\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        adminToken = adminLogin.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];

        var userRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"whuser\",\"email\":\"whuser@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        userToken = userRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
    }

    @Test
    void webhookEndpoints_shouldDenyRegularUser() throws Exception {
        mockMvc.perform(get("/api/webhooks")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void createWebhook_shouldSucceed() throws Exception {
        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/hook\",\"events\":[\"UPLOAD\",\"DELETE\"],\"secret\":\"mysecret\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.com/hook"))
                .andExpect(jsonPath("$.events", containsInAnyOrder("UPLOAD", "DELETE")))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void listWebhooks_shouldReturnPage() throws Exception {
        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/hook1\",\"events\":[\"UPLOAD\"],\"secret\":\"s1\"}")
                        .header("Authorization", "Bearer " + adminToken));
        mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/hook2\",\"events\":[\"DELETE\"],\"secret\":\"s2\"}")
                        .header("Authorization", "Bearer " + adminToken));

        mockMvc.perform(get("/api/webhooks")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    void updateWebhook_shouldModify() throws Exception {
        var createRes = mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://old.com/hook\",\"events\":[\"UPLOAD\"],\"secret\":\"old\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        Long whId = Long.parseLong(createRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(put("/api/webhooks/" + whId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://new.com/hook\",\"events\":[\"UPDATE\",\"DELETE\"],\"secret\":\"new\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://new.com/hook"))
                .andExpect(jsonPath("$.events", containsInAnyOrder("UPDATE", "DELETE")));
    }

    @Test
    void deleteWebhook_shouldRemove() throws Exception {
        var createRes = mockMvc.perform(post("/api/webhooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://del.com/hook\",\"events\":[\"UPLOAD\"],\"secret\":\"del\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        Long whId = Long.parseLong(createRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(delete("/api/webhooks/" + whId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void webhookEndpoints_shouldDenyUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/webhooks")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andExpect(status().isUnauthorized());
    }
}

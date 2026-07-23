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

import dev.securecdms.model.Role;
import dev.securecdms.repository.UserRepository;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecureDmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    private String userToken;
    private String adminToken;
    private Long normalUserId;
    private Long docId;

    @BeforeEach
    void setUp() throws Exception {
        var normalRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"normaluser\",\"email\":\"user@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        normalUserId = Long.parseLong(normalRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        var adminRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"adminuser\",\"email\":\"admin@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        String adminBody = adminRes.getResponse().getContentAsString();
        adminToken = adminBody.split("\"token\":\"")[1].split("\"")[0];

        // Manually promote user to ADMIN directly via repository
        userRepository.findByUsername("adminuser").ifPresent(u -> { u.setRole(Role.ROLE_ADMIN); userRepository.save(u); });

        // Re-login admin to get fresh token with ADMIN role
        var loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"adminuser\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        adminToken = loginRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];

        // Register another normal user for testing
        var userRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"anotheruser\",\"email\":\"another@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        userToken = userRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];

        // Upload a doc as normal user
        MockMultipartFile file = new MockMultipartFile("file", "admin-test.txt", "text/plain", "Admin test".getBytes());
        var uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file).param("description", "admin test")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk()).andReturn();
        docId = Long.parseLong(uploadRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
    }

    @Test
    void adminEndpoints_shouldDenyRegularUser() throws Exception {
        for (var req : new String[]{
                "/api/admin/stats",
                "/api/admin/users",
                "/api/admin/audit-logs",
                "/api/admin/retention-policies",
                "/api/admin/legal-holds",
                "/api/admin/system/config"
        }) {
            mockMvc.perform(get(req).header("Authorization", "Bearer " + userToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void getStats_shouldReturnSystemStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCount").isNumber())
                .andExpect(jsonPath("$.documentCount").isNumber())
                .andExpect(jsonPath("$.auditLogCount24h").isNumber());
    }

    @Test
    void getUsers_shouldReturnPaginatedUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[*].username").isNotEmpty());
    }

    @Test
    void updateUserRole_shouldPromoteToAdmin() throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + normalUserId + "/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void setUserEnabled_shouldDisableUser() throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + normalUserId + "/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void getAuditLogs_shouldReturnFilteredLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getAuditLogs_shouldFilterByAction() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs?action=UPLOAD")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    // ---- Retention Policies ----

    @Test
    void createRetentionPolicy_shouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/retention-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Policy\",\"documentType\":\"PDF\",\"retentionDays\":90,\"action\":\"DELETE\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Policy"))
                .andExpect(jsonPath("$.retentionDays").value(90));
    }

    @Test
    void getRetentionPolicies_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/admin/retention-policies")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void updateRetentionPolicy_shouldModifyExisting() throws Exception {
        var createRes = mockMvc.perform(post("/api/admin/retention-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Update Policy\",\"documentType\":\"PDF\",\"retentionDays\":30,\"action\":\"DELETE\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        Long policyId = Long.parseLong(createRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(put("/api/admin/retention-policies/" + policyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Policy\",\"documentType\":\"PDF\",\"retentionDays\":60,\"action\":\"ARCHIVE\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Policy"))
                .andExpect(jsonPath("$.retentionDays").value(60))
                .andExpect(jsonPath("$.action").value("ARCHIVE"));
    }

    @Test
    void deleteRetentionPolicy_shouldRemove() throws Exception {
        var createRes = mockMvc.perform(post("/api/admin/retention-policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delete Policy\",\"documentType\":\"PDF\",\"retentionDays\":30,\"action\":\"DELETE\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        Long policyId = Long.parseLong(createRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(delete("/api/admin/retention-policies/" + policyId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    // ---- Legal Holds ----

    @Test
    void createLegalHold_shouldSucceed() throws Exception {
        mockMvc.perform(post("/api/admin/legal-holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":" + docId + ",\"reason\":\"Legal investigation\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Legal investigation"))
                .andExpect(jsonPath("$.releasedAt").isEmpty());
    }

    @Test
    void releaseLegalHold_shouldClear() throws Exception {
        var createRes = mockMvc.perform(post("/api/admin/legal-holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":" + docId + ",\"reason\":\"Temp hold\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn();
        Long holdId = Long.parseLong(createRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(post("/api/admin/legal-holds/" + holdId + "/release")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedAt").isString());
    }

    @Test
    void getLegalHolds_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/admin/legal-holds")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ---- System Config ----

    @Test
    void getSystemConfig_shouldReturnConfig() throws Exception {
        mockMvc.perform(get("/api/admin/system/config")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    void updateSystemConfig_shouldPersist() throws Exception {
        mockMvc.perform(put("/api/admin/system/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxUploadSize\":\"50MB\"}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxUploadSize").value("50MB"));
    }
}

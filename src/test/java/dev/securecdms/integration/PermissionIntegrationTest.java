package dev.securecdms.integration;

import dev.securecdms.SecureDmsApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecureDmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PermissionIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String ownerToken;
    private String otherToken;
    private String otherToken2;
    private Long docId;
    private Long otherUserId;

    @BeforeEach
    void setUp() throws Exception {
        var ownerRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"permowner\",\"email\":\"owner@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        ownerToken = ownerRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];

        var otherRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"permuser\",\"email\":\"perm@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        otherToken = otherRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
        otherUserId = Long.parseLong(otherRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        var other2Res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"thirduser\",\"email\":\"third@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        otherToken2 = other2Res.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];

        MockMultipartFile file = new MockMultipartFile("file", "sharedoc.txt", "text/plain", "Shared content".getBytes());
        var uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file).param("description", "share test")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andReturn();
        docId = Long.parseLong(uploadRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
    }

    @Test
    void grantPermission_shouldAllowAccess() throws Exception {
        mockMvc.perform(post("/api/documents/" + docId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUserId + ",\"permissionType\":\"READ\"}")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionType").value("READ"));
    }

    @Test
    void grantPermission_shouldDenyNonOwner() throws Exception {
        mockMvc.perform(post("/api/documents/" + docId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUserId + ",\"permissionType\":\"READ\"}")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listPermissions_shouldReturnPermissions() throws Exception {
        mockMvc.perform(post("/api/documents/" + docId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUserId + ",\"permissionType\":\"READ\"}")
                        .header("Authorization", "Bearer " + ownerToken));

        mockMvc.perform(get("/api/documents/" + docId + "/permissions")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].permissionType").value("READ"));
    }

    @Test
    void revokePermission_shouldRemoveAccess() throws Exception {
        mockMvc.perform(post("/api/documents/" + docId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUserId + ",\"permissionType\":\"READ\"}")
                        .header("Authorization", "Bearer " + ownerToken));

        mockMvc.perform(delete("/api/documents/" + docId + "/permissions/" + otherUserId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void sharedUser_shouldBeAbleToDownload() throws Exception {
        mockMvc.perform(post("/api/documents/" + docId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUserId + ",\"permissionType\":\"READ\"}")
                        .header("Authorization", "Bearer " + ownerToken));

        mockMvc.perform(get("/api/documents/" + docId + "/download")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedAccess_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/documents/" + docId + "/permissions"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/documents/" + docId + "/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + otherUserId + ",\"permissionType\":\"READ\"}"))
                .andExpect(status().isUnauthorized());
    }
}

package dev.securecdms.integration;

import dev.securecdms.SecureDmsApplication;
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
class FolderIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String token;
    private Long folderId;

    @BeforeEach
    void setUp() throws Exception {
        var res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"folderuser\",\"email\":\"folder@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        token = res.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
    }

    @Test
    void createFolder_shouldSucceed() throws Exception {
        var result = mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Folder\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Folder"))
                .andReturn();
        folderId = Long.parseLong(result.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
    }

    @Test
    void createFolder_shouldReturn400WhenNameMissing() throws Exception {
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFolders_shouldReturnOwnFolders() throws Exception {
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Folder A\"}")
                        .header("Authorization", "Bearer " + token));
        mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Folder B\"}")
                        .header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/folders")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void renameFolder_shouldUpdate() throws Exception {
        var createRes = mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Old Name\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.parseLong(createRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(put("/api/folders/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Name"));
    }

    @Test
    void deleteFolder_shouldRemove() throws Exception {
        var createRes = mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Delete Me\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.parseLong(createRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(delete("/api/folders/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void folderEndpoints_shouldDenyUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/folders")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/folders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/folders/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/folders/1")).andExpect(status().isUnauthorized());
    }
}

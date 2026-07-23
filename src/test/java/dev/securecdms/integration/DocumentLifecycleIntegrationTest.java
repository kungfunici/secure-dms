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
class DocumentLifecycleIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String token;
    private Long docId;
    private Long tagId;
    private Long folderId;

    @BeforeEach
    void setUp() throws Exception {
        var res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"lifecycle\",\"email\":\"life@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        token = res.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];

        MockMultipartFile file = new MockMultipartFile("file", "lifecycle.txt", "text/plain", "Lifecycle content".getBytes());
        var uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file).param("description", "lifecycle test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        docId = Long.parseLong(uploadRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        var folderRes = mockMvc.perform(post("/api/folders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Lifecycle Folder\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        folderId = Long.parseLong(folderRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        var tagRes = mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"important\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        tagId = Long.parseLong(tagRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
    }

    @Test
    void getById_shouldReturnDocument() throws Exception {
        mockMvc.perform(get("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("lifecycle.txt"));
    }

    @Test
    void preview_shouldReturnInlineContent() throws Exception {
        mockMvc.perform(get("/api/documents/" + docId + "/preview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline"))
                .andExpect(content().string("Lifecycle content"));
    }

    @Test
    void moveToFolder_shouldUpdateLocation() throws Exception {
        mockMvc.perform(patch("/api/documents/" + docId + "/move?folderId=" + folderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.folderId").value(folderId.intValue()));
    }

    @Test
    void trashAndRestore_shouldWork() throws Exception {
        mockMvc.perform(delete("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/trash")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalFilename").value("lifecycle.txt"));

        mockMvc.perform(patch("/api/documents/" + docId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").doesNotExist());
    }

    @Test
    void versions_shouldListAfterUpdate() throws Exception {
        MockMultipartFile updated = new MockMultipartFile("file", "v2.txt", "text/plain", "Version 2".getBytes());
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/documents/" + docId)
                        .file(updated).param("description", "version 2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/" + docId + "/versions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].versionNumber").value(2));
    }

    @Test
    void restoreVersion_shouldRestore() throws Exception {
        MockMultipartFile updated = new MockMultipartFile("file", "v2.txt", "text/plain", "Version 2".getBytes());
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/documents/" + docId)
                        .file(updated).param("description", "version 2")
                        .header("Authorization", "Bearer " + token));

        var versionsRes = mockMvc.perform(get("/api/documents/" + docId + "/versions")
                        .header("Authorization", "Bearer " + token)).andReturn();
        String versionsJson = versionsRes.getResponse().getContentAsString();
        Long versionId = Long.parseLong(versionsJson.split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(post("/api/documents/" + docId + "/versions/" + versionId + "/restore")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void duplicate_shouldCreateCopy() throws Exception {
        mockMvc.perform(post("/api/documents/" + docId + "/duplicate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename", containsString("lifecycle")))
                .andExpect(jsonPath("$.id", not(docId.intValue())));
    }

    @Test
    void addAndRemoveTag_shouldWork() throws Exception {
        mockMvc.perform(post("/api/documents/" + docId + "/tags/" + tagId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", hasItem("important")));

        mockMvc.perform(delete("/api/documents/" + docId + "/tags/" + tagId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", not(hasItem("important"))));
    }

    @Test
    void toggleFavorite_shouldWork() throws Exception {
        mockMvc.perform(post("/api/documents/" + docId + "/favorite")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(true));

        mockMvc.perform(get("/api/documents/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/documents/" + docId + "/favorite")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(false));
    }

    @Test
    void batchDelete_shouldRemoveAll() throws Exception {
        MockMultipartFile f1 = new MockMultipartFile("file", "batch1.txt", "text/plain", "B1".getBytes());
        var r1 = mockMvc.perform(multipart("/api/documents/upload").file(f1).param("description", "b1")
                        .header("Authorization", "Bearer " + token)).andReturn();
        Long id1 = Long.parseLong(r1.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        MockMultipartFile f2 = new MockMultipartFile("file", "batch2.txt", "text/plain", "B2".getBytes());
        var r2 = mockMvc.perform(multipart("/api/documents/upload").file(f2).param("description", "b2")
                        .header("Authorization", "Bearer " + token)).andReturn();
        Long id2 = Long.parseLong(r2.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(post("/api/documents/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + id1 + "," + id2 + "]")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void batchMove_shouldMoveAll() throws Exception {
        MockMultipartFile f1 = new MockMultipartFile("file", "mv1.txt", "text/plain", "M1".getBytes());
        var r1 = mockMvc.perform(multipart("/api/documents/upload").file(f1).param("description", "m1")
                        .header("Authorization", "Bearer " + token)).andReturn();
        Long id1 = Long.parseLong(r1.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        MockMultipartFile f2 = new MockMultipartFile("file", "mv2.txt", "text/plain", "M2".getBytes());
        var r2 = mockMvc.perform(multipart("/api/documents/upload").file(f2).param("description", "m2")
                        .header("Authorization", "Bearer " + token)).andReturn();
        Long id2 = Long.parseLong(r2.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(post("/api/documents/batch/move?folderId=" + folderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + id1 + "," + id2 + "]")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void batchDownload_shouldReturnZip() throws Exception {
        MockMultipartFile f1 = new MockMultipartFile("file", "dl1.txt", "text/plain", "DL1".getBytes());
        var r1 = mockMvc.perform(multipart("/api/documents/upload").file(f1).param("description", "dl1")
                        .header("Authorization", "Bearer " + token)).andReturn();
        Long id1 = Long.parseLong(r1.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        MockMultipartFile f2 = new MockMultipartFile("file", "dl2.txt", "text/plain", "DL2".getBytes());
        var r2 = mockMvc.perform(multipart("/api/documents/upload").file(f2).param("description", "dl2")
                        .header("Authorization", "Bearer " + token)).andReturn();
        Long id2 = Long.parseLong(r2.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        mockMvc.perform(post("/api/documents/batch/download")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + id1 + "," + id2 + "]")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".zip")))
                .andExpect(content().contentType("application/zip"));
    }

    @Test
    void recentlyViewed_shouldReturnRecentDocs() throws Exception {
        mockMvc.perform(get("/api/documents/" + docId + "/download")
                        .header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/documents/recently-viewed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(docId.intValue()));
    }

    @Test
    void lifecycleEndpoints_shouldDenyUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/documents/" + docId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/documents/" + docId + "/preview")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/documents/trash")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/documents/recently-viewed")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/documents/" + docId + "/restore")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/documents/" + docId + "/versions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/documents/" + docId + "/duplicate")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/documents/" + docId + "/favorite")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/documents/favorites")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/documents/batch/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("[]")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/documents/batch/download").contentType(MediaType.APPLICATION_JSON)
                        .content("[]")).andExpect(status().isUnauthorized());
    }
}

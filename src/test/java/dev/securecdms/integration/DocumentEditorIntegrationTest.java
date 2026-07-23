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
class DocumentEditorIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String token;
    private Long textDocId;
    private Long pdfDocId;

    @BeforeEach
    void setUp() throws Exception {
        var res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"editoruser\",\"email\":\"editor@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        token = body.split("\"token\":\"")[1].split("\"")[0];

        MockMultipartFile txtFile = new MockMultipartFile("file", "hello.txt", "text/plain", "Hello World".getBytes());
        var uploadTxt = mockMvc.perform(multipart("/api/documents/upload")
                        .file(txtFile).param("description", "text doc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        textDocId = Long.parseLong(uploadTxt.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        MockMultipartFile pdfFile = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-fake".getBytes());
        var uploadPdf = mockMvc.perform(multipart("/api/documents/upload")
                        .file(pdfFile).param("description", "pdf doc")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        pdfDocId = Long.parseLong(uploadPdf.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
    }

    @Test
    void getContent_shouldReturnTextContent() throws Exception {
        mockMvc.perform(get("/api/documents/" + textDocId + "/content")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hello World")));
    }

    @Test
    void getContent_shouldReturn404ForNonTextDocument() throws Exception {
        mockMvc.perform(get("/api/documents/" + pdfDocId + "/content")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateContent_shouldSaveAndCreateVersion() throws Exception {
        mockMvc.perform(put("/api/documents/" + textDocId + "/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Updated text\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2));

        mockMvc.perform(get("/api/documents/" + textDocId + "/content")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Updated text"));
    }

    @Test
    void updateContent_shouldReturn400WhenContentMissing() throws Exception {
        mockMvc.perform(put("/api/documents/" + textDocId + "/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void render_shouldReturnHtmlForTextDocument() throws Exception {
        mockMvc.perform(get("/api/documents/" + textDocId + "/render")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hello World")));
    }

    @Test
    void render_shouldReturn400ForNonConvertibleType() throws Exception {
        mockMvc.perform(get("/api/documents/" + pdfDocId + "/render")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("not editable")));
    }

    @Test
    void saveRendered_shouldUpdateTextDocument() throws Exception {
        mockMvc.perform(put("/api/documents/" + textDocId + "/save-rendered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<p>Rendered content</p>\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2));
    }

    @Test
    void saveRendered_shouldReturn400WhenHtmlMissing() throws Exception {
        mockMvc.perform(put("/api/documents/" + textDocId + "/save-rendered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing html field"));
    }

    @Test
    void saveRendered_shouldReturn400ForNonConvertibleType() throws Exception {
        mockMvc.perform(put("/api/documents/" + pdfDocId + "/save-rendered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<p>test</p>\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("not editable")));
    }

    @Test
    void render_shouldDenyUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/documents/" + textDocId + "/render"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saveRendered_shouldDenyUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/documents/" + textDocId + "/save-rendered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"html\":\"<p>test</p>\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contentEndpoints_shouldDenyUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/documents/" + textDocId + "/content"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/documents/" + textDocId + "/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }
}

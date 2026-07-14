package dev.securecdms.integration;

import dev.securecdms.SecureDmsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecureDmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecureDmsApplicationTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void health_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void fullAuthFlow_shouldSucceed() throws Exception {
        // Register
        String registerBody = """
                {"username":"integration","email":"integration@test.com","password":"testpass123"}
                """;

        var registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andReturn();

        String token = registerResult.getResponse().getCookie("token") != null
                ? null : registerResult.getResponse().getContentAsString();
        // Extract token from JSON
        String accessToken = registerResult.getResponse().getContentAsString()
                .split("\"token\":\"")[1].split("\"")[0];
        String refreshToken = registerResult.getResponse().getContentAsString()
                .split("\"refreshToken\":\"")[1].split("\"")[0];

        // Login
        String loginBody = """
                {"username":"integration","password":"testpass123"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));

        // Refresh
        String refreshBody = "{\"refreshToken\":\"" + refreshToken + "\"}";

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));

        // Upload document
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "Hello World".getBytes());

        var uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("description", "Integration test file")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("test.txt"))
                .andReturn();

        String docJson = uploadResult.getResponse().getContentAsString();
        Long docId = Long.parseLong(docJson.split("\"id\":")[1].split(",")[0]);

        // List documents
        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalFilename").value("test.txt"));

        // Search documents
        mockMvc.perform(get("/api/documents/search?q=integration")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value(containsString("Integration test")));

        // Download document
        mockMvc.perform(get("/api/documents/" + docId + "/download")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("test.txt")))
                .andExpect(content().string("Hello World"));

        // Update document description
        MockMultipartFile updatedFile = new MockMultipartFile("file", "updated.txt", "text/plain", "Updated content".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/" + docId)
                        .file(updatedFile)
                        .param("description", "Updated integration test file")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("updated.txt"));

        // Delete document
        mockMvc.perform(delete("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void unauthenticatedAccess_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authEndpoints_shouldBePublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isUnauthorized());
    }
}

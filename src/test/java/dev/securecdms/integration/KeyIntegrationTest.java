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
class KeyIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String token;
    private Long userId;
    private String otherToken;
    private Long otherUserId;
    private Long docId;

    @BeforeEach
    void setUp() throws Exception {
        var res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"keyowner\",\"email\":\"keyown@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        token = res.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
        userId = Long.parseLong(res.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        var otherRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"keyother\",\"email\":\"keyoth@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        otherToken = otherRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
        otherUserId = Long.parseLong(otherRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);

        MockMultipartFile file = new MockMultipartFile("file", "key-test.txt", "text/plain", "Key content".getBytes());
        var uploadRes = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file).param("description", "key test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        docId = Long.parseLong(uploadRes.getResponse().getContentAsString().split("\"id\":")[1].split(",")[0]);
    }

    @Test
    void myKeyStatus_shouldReturnFalseInitially() throws Exception {
        mockMvc.perform(get("/api/keys/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPublicKey").value(false));
    }

    @Test
    void uploadPublicKey_shouldSucceed() throws Exception {
        mockMvc.perform(post("/api/keys/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicKey\":\"MIIBCgKCAQEA...\",\"algorithm\":\"RSA-OAEP\"}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/keys/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPublicKey").value(true));
    }

    @Test
    void uploadPublicKey_shouldReturn400WhenKeyMissing() throws Exception {
        mockMvc.perform(post("/api/keys/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserPublicKey_shouldReturnKey() throws Exception {
        mockMvc.perform(post("/api/keys/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicKey\":\"test-public-key-data\"}")
                        .header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/keys/me")
                        .header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/keys/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("test-public-key-data"));
    }

    @Test
    void storeAndGetWrappedKey_shouldWork() throws Exception {
        mockMvc.perform(post("/api/keys/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicKey\":\"key-for-other\"}")
                        .header("Authorization", "Bearer " + otherToken));

        mockMvc.perform(post("/api/keys/documents/" + docId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userKeys\":[{\"userId\":" + otherUserId + ",\"wrappedKey\":\"" +
                                java.util.Base64.getEncoder().encodeToString("wrapped-secret".getBytes()) + "\"}]}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/keys/documents/" + docId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wrappedKey").isString());
    }

    @Test
    void wrappedKeyEndpoints_shouldDenyUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/keys/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/keys/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/keys/documents/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/keys/documents/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{}")).andExpect(status().isUnauthorized());
    }
}

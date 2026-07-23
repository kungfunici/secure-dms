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
class UserProfileIntegrationTest {

    @Autowired private MockMvc mockMvc;

    private String token;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        var res = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"profileuser\",\"email\":\"profile@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        String body = res.getResponse().getContentAsString();
        token = body.split("\"token\":\"")[1].split("\"")[0];
        userId = Long.parseLong(body.split("\"id\":")[1].split(",")[0]);
    }

    @Test
    void getProfile_shouldReturnUser() throws Exception {
        mockMvc.perform(get("/api/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("profileuser"))
                .andExpect(jsonPath("$.email").value("profile@test.com"));
    }

    @Test
    void updateProfile_shouldSetVersionRetention() throws Exception {
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/" + userId)
                        .param("versionRetentionDays", "30")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionRetentionDays").value(30));
    }

    @Test
    void updateProfile_shouldSetAvatar() throws Exception {
        MockMultipartFile avatar = new MockMultipartFile("avatar", "avatar.jpg", "image/jpeg", "fake-image-data".getBytes());
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/" + userId)
                        .file(avatar)
                        .param("versionRetentionDays", "7")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePicture").isNotEmpty());
    }

    @Test
    void updateProfile_shouldDenyOtherUser() throws Exception {
        var otherRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"otherprofile\",\"email\":\"otherp@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        String otherToken = otherRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];

        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/" + userId)
                        .param("versionRetentionDays", "90")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAvatar_shouldReturn404WhenNoAvatar() throws Exception {
        mockMvc.perform(get("/api/users/" + userId + "/avatar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvatar_shouldReturnImageAfterUpload() throws Exception {
        MockMultipartFile avatar = new MockMultipartFile("avatar", "pic.jpg", "image/jpeg", "imgdata".getBytes());
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/api/users/" + userId)
                        .file(avatar).param("versionRetentionDays", "7")
                        .header("Authorization", "Bearer " + token));

        mockMvc.perform(get("/api/users/" + userId + "/avatar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void searchUsers_shouldReturnMatching() throws Exception {
        mockMvc.perform(get("/api/users/search?q=profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].username").value("profileuser"));
    }

    @Test
    void deleteAccount_shouldRemoveUser() throws Exception {
        mockMvc.perform(delete("/api/users/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteAccount_shouldDenyOtherUser() throws Exception {
        var otherRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"delother\",\"email\":\"delo@test.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isOk()).andReturn();
        String otherToken = otherRes.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];

        mockMvc.perform(delete("/api/users/" + userId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }
}

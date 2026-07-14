package dev.securecdms.controller;

import dev.securecdms.dto.response.UserResponse;
import dev.securecdms.service.StorageService;
import dev.securecdms.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private UserService userService;
    @MockBean private StorageService storageService;

    private final UserResponse userResponse = UserResponse.builder()
            .id(1L).username("testuser").email("test@example.com")
            .profilePicture(null).createdAt(Instant.now()).build();

    @Test
    @WithMockUser
    void getProfile_shouldReturnUser() throws Exception {
        when(userService.getProfile(1L)).thenReturn(userResponse);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @WithMockUser
    void searchUsers_shouldReturnList() throws Exception {
        when(userService.searchUsers("test")).thenReturn(List.of(userResponse));

        mockMvc.perform(get("/api/users/search?q=test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("testuser"));
    }

    @Test
    @WithMockUser
    void getAvatar_shouldReturn404WhenNoAvatar() throws Exception {
        when(userService.getAvatarPath(1L)).thenReturn(null);

        mockMvc.perform(get("/api/users/1/avatar"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void deleteAccount_shouldReturnNoContent() throws Exception {
        doNothing().when(userService).deleteAccount(1L, "user");

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent());
    }
}

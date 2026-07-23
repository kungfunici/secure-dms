package dev.securecdms.controller;

import dev.securecdms.dto.response.TagResponse;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Tag;
import dev.securecdms.model.User;
import dev.securecdms.repository.TagRepository;
import dev.securecdms.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TagControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private TagRepository tagRepository;
    @MockBean private UserRepository userRepository;

    private final User user = User.builder().id(1L).username("user").build();
    private final Tag tag = Tag.builder().id(1L).name("important").color("#ff0000").createdBy(user).createdAt(Instant.now()).build();
    private final TagResponse tagResponse = TagResponse.builder().id(1L).name("important").color("#ff0000").build();

    @Test
    @WithMockUser
    void list_shouldReturnAllTags() throws Exception {
        when(tagRepository.findAll()).thenReturn(List.of(tag));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("important"));
    }

    @Test
    @WithMockUser
    void create_shouldReturnCreatedTag() throws Exception {
        when(tagRepository.existsByName("important")).thenReturn(false);
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));
        when(tagRepository.save(any())).thenReturn(tag);

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"important\",\"color\":\"#ff0000\"}")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("important"))
                .andExpect(jsonPath("$.color").value("#ff0000"));
    }

    @Test
    @WithMockUser
    void create_shouldReturn400WhenNameMissing() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"color\":\"#ff0000\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void create_shouldReturn400WhenDuplicate() throws Exception {
        when(tagRepository.existsByName("important")).thenReturn(true);

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"important\"}")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user")
    void delete_shouldAllowOwner() throws Exception {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/tags/1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(tagRepository).deleteById(1L);
    }

    @Test
    @WithMockUser(username = "other")
    void delete_shouldDenyNonOwner() throws Exception {
        User other = User.builder().id(2L).username("other").build();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(other));

        mockMvc.perform(delete("/api/tags/1").with(csrf()))
                .andExpect(status().isForbidden());

        verify(tagRepository, never()).deleteById(any());
    }

    @Test
    @WithMockUser
    void delete_shouldReturn404WhenTagNotFound() throws Exception {
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/tags/99").with(csrf()))
                .andExpect(status().isNotFound());
    }
}

package dev.securecdms.controller;

import dev.securecdms.dto.response.DocumentResponse;
import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.service.DocumentService;
import dev.securecdms.service.DocumentService.DownloadResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private DocumentService documentService;

    private final DocumentResponse docResponse = DocumentResponse.builder()
            .id(1L).originalFilename("test.pdf").contentType("application/pdf")
            .fileSize(1024L).description("Test doc").ownerUsername("user")
            .uploadedAt(Instant.now()).build();

    @Test
    @WithMockUser
    void upload_shouldReturnDocument() throws Exception {
        when(documentService.upload(any(), any(), isNull(), isNull(), anyString())).thenReturn(docResponse);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("description", "Test doc")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("test.pdf"));
    }

    @Test
    @WithMockUser
    void listMyDocuments_shouldReturnPage() throws Exception {
        when(documentService.listMyDocuments(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(docResponse)));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalFilename").value("test.pdf"));
    }

    @Test
    @WithMockUser
    void search_shouldReturnResults() throws Exception {
        when(documentService.search(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(docResponse)));

        mockMvc.perform(get("/api/documents/search").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalFilename").value("test.pdf"));
    }

    @Test
    @WithMockUser
    void download_shouldReturnFile() throws Exception {
        Path tempFile = Files.createTempFile("test", ".pdf");
        Files.writeString(tempFile, "test content");
        tempFile.toFile().deleteOnExit();

        when(documentService.download(anyLong(), anyString()))
                .thenReturn(new DownloadResult(tempFile, "report.pdf"));

        mockMvc.perform(get("/api/documents/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report.pdf\""))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    @WithMockUser
    void download_shouldReturn403WhenDenied() throws Exception {
        when(documentService.download(anyLong(), anyString()))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/documents/1/download"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void download_shouldReturn404WhenNotFound() throws Exception {
        when(documentService.download(anyLong(), anyString()))
                .thenThrow(new ResourceNotFoundException("Document not found"));

        mockMvc.perform(get("/api/documents/1/download"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void delete_shouldReturn204() throws Exception {
        doNothing().when(documentService).delete(anyLong(), anyString());

        mockMvc.perform(delete("/api/documents/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void delete_shouldReturn403WhenDenied() throws Exception {
        doThrow(new AccessDeniedException("Access denied")).when(documentService).delete(anyLong(), anyString());

        mockMvc.perform(delete("/api/documents/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void sharedWithMe_shouldReturnPage() throws Exception {
        when(documentService.listSharedWithMe(anyString(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(docResponse)));

        mockMvc.perform(get("/api/documents/shared-with-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalFilename").value("test.pdf"));
    }

    @Test
    @WithMockUser
    void sharedByMe_shouldReturnPage() throws Exception {
        when(documentService.listSharedByMe(anyString(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(docResponse)));

        mockMvc.perform(get("/api/documents/shared-by-me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].originalFilename").value("test.pdf"));
    }

    @Test
    @WithMockUser
    void moveToFolder_shouldReturnUpdated() throws Exception {
        when(documentService.moveToFolder(eq(1L), eq(2L), anyString()))
                .thenReturn(docResponse);

        mockMvc.perform(patch("/api/documents/1/move?folderId=2").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("test.pdf"));
    }

    @Test
    @WithMockUser
    void update_shouldReturnUpdatedDocument() throws Exception {
        when(documentService.update(anyLong(), anyString(), any(), any()))
                .thenReturn(docResponse);

        MockMultipartFile file = new MockMultipartFile("file", "new.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/documents/1")
                        .file(file)
                        .param("description", "Updated desc")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("test.pdf"));
    }

    @Test
    void unauthenticated_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }
}

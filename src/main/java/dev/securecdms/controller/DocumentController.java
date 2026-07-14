package dev.securecdms.controller;

import dev.securecdms.dto.response.DocumentResponse;
import dev.securecdms.dto.response.VersionResponse;
import dev.securecdms.service.DocumentService;
import dev.securecdms.service.DocumentService.DownloadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        DocumentResponse response = documentService.upload(file, description, documentType, folderId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> update(
            @PathVariable Long id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        DocumentResponse response = documentService.update(id, userDetails.getUsername(), file, description);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<DocumentResponse> moveToFolder(
            @PathVariable Long id,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @AuthenticationPrincipal UserDetails userDetails) {

        DocumentResponse response = documentService.moveToFolder(id, folderId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listMyDocuments(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {

        return ResponseEntity.ok(documentService.listMyDocuments(userDetails.getUsername(), pageable));
    }

    @GetMapping("/trash")
    public ResponseEntity<Page<DocumentResponse>> listTrash(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {

        return ResponseEntity.ok(documentService.listTrash(userDetails.getUsername(), pageable));
    }

    @GetMapping("/recently-viewed")
    public ResponseEntity<Page<DocumentResponse>> recentlyViewed(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {

        return ResponseEntity.ok(documentService.listRecentlyViewed(userDetails.getUsername(), pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<DocumentResponse>> search(
            @RequestParam("q") String query,
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {

        return ResponseEntity.ok(documentService.search(userDetails.getUsername(), query, pageable));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        DownloadResult result = documentService.download(id, userDetails.getUsername());
        Resource resource = new PathResource(result.path());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.originalFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        DownloadResult result = documentService.preview(id, userDetails.getUsername());
        Resource resource = new PathResource(result.path());

        String contentType = "application/octet-stream";
        try {
            String probe = Files.probeContentType(result.path());
            if (probe != null) contentType = probe;
        } catch (IOException ignored) {}

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<Page<DocumentResponse>> listSharedWithMe(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {

        return ResponseEntity.ok(
                documentService.listSharedWithMe(userDetails.getUsername(), pageable));
    }

    @GetMapping("/shared-by-me")
    public ResponseEntity<Page<DocumentResponse>> listSharedByMe(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {

        return ResponseEntity.ok(
                documentService.listSharedByMe(userDetails.getUsername(), pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        documentService.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restore")
    public ResponseEntity<DocumentResponse> restore(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        DocumentResponse response = documentService.restore(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    // ---- Text Content Editing ----

    @GetMapping("/{id}/content")
    public ResponseEntity<String> getContent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String content = documentService.getContent(id, userDetails.getUsername());
        return ResponseEntity.ok(content);
    }

    @PutMapping("/{id}/content")
    public ResponseEntity<DocumentResponse> updateContent(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        String content = body.get("content");
        if (content == null) {
            return ResponseEntity.badRequest().build();
        }
        DocumentResponse response = documentService.updateContent(id, content, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    // ---- Versions ----

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<VersionResponse>> getVersions(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(documentService.getVersions(id, userDetails.getUsername()));
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public ResponseEntity<DocumentResponse> restoreVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        DocumentResponse response = documentService.restoreVersion(id, versionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    // ---- Batch operations ----

    @PostMapping("/batch/delete")
    public ResponseEntity<Void> batchDelete(
            @RequestBody List<Long> ids,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        documentService.batchDelete(ids, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch/move")
    public ResponseEntity<Void> batchMove(
            @RequestParam("folderId") Long folderId,
            @RequestBody List<Long> ids,
            @AuthenticationPrincipal UserDetails userDetails) {

        documentService.batchMove(ids, folderId, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch/download")
    public ResponseEntity<Resource> batchDownload(
            @RequestBody List<Long> ids,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        documentService.batchDownload(ids, userDetails.getUsername(), baos);
        byte[] zipData = baos.toByteArray();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"documents.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new ByteArrayResource(zipData));
    }
}

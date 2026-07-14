package dev.securecdms.controller;

import dev.securecdms.dto.request.FolderRequest;
import dev.securecdms.dto.response.FolderResponse;
import dev.securecdms.service.FolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    public ResponseEntity<FolderResponse> create(@Valid @RequestBody FolderRequest request,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(folderService.create(request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<FolderResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(folderService.list(userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FolderResponse> rename(@PathVariable Long id,
                                                  @Valid @RequestBody FolderRequest request,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(folderService.rename(id, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        folderService.delete(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}

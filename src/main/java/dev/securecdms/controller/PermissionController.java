package dev.securecdms.controller;

import dev.securecdms.dto.request.ShareRequest;
import dev.securecdms.dto.response.PermissionResponse;
import dev.securecdms.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<PermissionResponse>> list(@PathVariable Long documentId,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(permissionService.list(documentId, userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<PermissionResponse> grant(@PathVariable Long documentId,
                                                     @Valid @RequestBody ShareRequest request,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(permissionService.grant(documentId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(@PathVariable Long documentId,
                                       @PathVariable Long userId,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        permissionService.revoke(documentId, userId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}

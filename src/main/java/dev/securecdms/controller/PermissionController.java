package dev.securecdms.controller;

import dev.securecdms.dto.request.PermissionRequest;
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
    public ResponseEntity<List<PermissionService.PermissionInfo>> list(
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(
                permissionService.listPermissions(documentId, userDetails.getUsername()));
    }

    @PostMapping
    public ResponseEntity<Void> grant(
            @PathVariable Long documentId,
            @Valid @RequestBody PermissionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        permissionService.grantPermission(
                documentId,
                userDetails.getUsername(),
                request.getUsername(),
                request.getPermissionType());

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{targetUsername}")
    public ResponseEntity<Void> revoke(
            @PathVariable Long documentId,
            @PathVariable String targetUsername,
            @AuthenticationPrincipal UserDetails userDetails) {

        permissionService.revokePermission(
                documentId,
                userDetails.getUsername(),
                targetUsername);

        return ResponseEntity.noContent().build();
    }
}

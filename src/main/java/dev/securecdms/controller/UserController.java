package dev.securecdms.controller;

import dev.securecdms.dto.response.UserResponse;
import dev.securecdms.service.StorageService;
import dev.securecdms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StorageService storageService;

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getProfile(id));
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<Resource> getAvatar(@PathVariable Long id) {
        String profilePicture = userService.getAvatarPath(id);
        if (profilePicture == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = storageService.load(profilePicture);
        Resource resource = new PathResource(path);
        String ext = profilePicture.contains(".") ? profilePicture.substring(profilePicture.lastIndexOf('.') + 1).toLowerCase() : "";
        MediaType mediaType = switch (ext) {
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.valueOf("image/webp");
            case "svg" -> MediaType.valueOf("image/svg+xml");
            case "bmp" -> MediaType.valueOf("image/bmp");
            default -> MediaType.IMAGE_JPEG;
        };
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .contentType(mediaType)
                .body(resource);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateProfile(
            @PathVariable Long id,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar,
            @RequestParam(value = "versionRetentionDays", required = false) Integer versionRetentionDays,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        UserResponse response = userService.updateProfile(id, userDetails.getUsername(), avatar, versionRetentionDays);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        userService.deleteAccount(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserResponse>> searchUsers(@RequestParam("q") String query) {
        return ResponseEntity.ok(userService.searchUsers(query));
    }
}

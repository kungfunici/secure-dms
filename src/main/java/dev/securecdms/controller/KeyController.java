package dev.securecdms.controller;

import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.User;
import dev.securecdms.repository.UserRepository;
import dev.securecdms.service.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class KeyController {

    private final CryptoService cryptoService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> myKeyStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        boolean hasKey = cryptoService.hasPublicKey(user.getId());
        return ResponseEntity.ok(Map.of(
                "hasPublicKey", hasKey,
                "userId", user.getId()
        ));
    }

    @PostMapping("/me")
    public ResponseEntity<Void> uploadPublicKey(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        String publicKey = body.get("publicKey");
        String algorithm = body.getOrDefault("algorithm", "RSA-OAEP");

        if (publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        cryptoService.storePublicKey(user, publicKey, algorithm);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> getUserPublicKey(@PathVariable Long userId) {
        String publicKey = cryptoService.getPublicKey(userId);
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<Map<String, Object>> getWrappedKey(
            @PathVariable Long documentId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        byte[] wrappedKey = cryptoService.getWrappedKey(documentId, user.getId());
        return ResponseEntity.ok(Map.of(
                "wrappedKey", java.util.Base64.getEncoder().encodeToString(wrappedKey)
        ));
    }

    @PostMapping("/documents/{documentId}")
    public ResponseEntity<Void> storeWrappedKey(
            @PathVariable Long documentId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        @SuppressWarnings("unchecked")
        var userKeys = (java.util.List<Map<String, Object>>) body.get("userKeys");
        if (userKeys == null) {
            return ResponseEntity.badRequest().build();
        }

        for (var entry : userKeys) {
            Long targetUserId = Long.valueOf(entry.get("userId").toString());
            byte[] wrappedKey = java.util.Base64.getDecoder().decode(entry.get("wrappedKey").toString());
            cryptoService.storeWrappedKey(documentId, targetUserId, wrappedKey);
        }

        return ResponseEntity.ok().build();
    }
}

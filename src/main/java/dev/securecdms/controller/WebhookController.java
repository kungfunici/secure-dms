package dev.securecdms.controller;

import dev.securecdms.dto.request.WebhookRequest;
import dev.securecdms.dto.response.WebhookResponse;
import dev.securecdms.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping
    public ResponseEntity<Page<WebhookResponse>> getWebhooks(Pageable pageable) {
        return ResponseEntity.ok(webhookService.getWebhooks(pageable));
    }

    @PostMapping
    public ResponseEntity<WebhookResponse> createWebhook(
            @RequestBody WebhookRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(webhookService.createWebhook(req, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WebhookResponse> updateWebhook(
            @PathVariable Long id, @RequestBody WebhookRequest req) {
        return ResponseEntity.ok(webhookService.updateWebhook(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable Long id) {
        webhookService.deleteWebhook(id);
        return ResponseEntity.noContent().build();
    }
}

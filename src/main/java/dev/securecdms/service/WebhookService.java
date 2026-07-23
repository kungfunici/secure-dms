package dev.securecdms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.securecdms.dto.request.WebhookRequest;
import dev.securecdms.dto.response.WebhookResponse;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.User;
import dev.securecdms.model.Webhook;
import dev.securecdms.repository.UserRepository;
import dev.securecdms.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Page<WebhookResponse> getWebhooks(Pageable pageable) {
        return webhookRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public WebhookResponse createWebhook(WebhookRequest req, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        Webhook hook = Webhook.builder()
                .url(req.getUrl())
                .events(req.getEvents() != null ? toJsonArray(req.getEvents()) : "[]")
                .secret(req.getSecret())
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .createdBy(user)
                .build();
        return toResponse(webhookRepository.save(hook));
    }

    @Transactional
    public WebhookResponse updateWebhook(Long id, WebhookRequest req) {
        Webhook hook = webhookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));
        hook.setUrl(req.getUrl());
        hook.setEvents(req.getEvents() != null ? toJsonArray(req.getEvents()) : hook.getEvents());
        hook.setSecret(req.getSecret());
        hook.setEnabled(req.getEnabled() != null ? req.getEnabled() : hook.getEnabled());
        return toResponse(webhookRepository.save(hook));
    }

    @Transactional
    public void deleteWebhook(Long id) {
        Webhook hook = webhookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));
        webhookRepository.delete(hook);
    }

    @Async
    public void dispatch(String event, Long documentId, String details, String username) {
        List<Webhook> hooks = webhookRepository.findByEnabledTrue();
        for (Webhook hook : hooks) {
            List<String> events = parseEvents(hook.getEvents());
            if (!events.contains(event)) continue;
            try {
                send(hook, event, documentId, details, username);
            } catch (Exception e) {
                log.error("Failed to deliver webhook {} to {}: {}", hook.getId(), hook.getUrl(), e.getMessage());
            }
        }
    }

    private void send(Webhook hook, String event, Long documentId, String details, String username) throws Exception {
        Map<String, Object> body = Map.of(
                "event", event,
                "documentId", documentId,
                "details", details,
                "username", username,
                "timestamp", System.currentTimeMillis()
        );
        String payload = objectMapper.writeValueAsString(body);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(hook.getUrl()))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10));

        if (hook.getSecret() != null && !hook.getSecret().isBlank()) {
            String signature = sign(payload, hook.getSecret());
            reqBuilder.header("X-Webhook-Signature", signature);
        }

        HttpRequest request = reqBuilder
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("Webhook {} delivered to {}: {}", hook.getId(), hook.getUrl(), response.statusCode());
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(key);
            byte[] hmac = mac.doFinal(payload.getBytes());
            return Base64.getEncoder().encodeToString(hmac);
        } catch (Exception e) {
            log.warn("Failed to sign webhook payload: {}", e.getMessage());
            return null;
        }
    }

    private String toJsonArray(List<String> events) {
        try {
            return objectMapper.writeValueAsString(events);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseEvents(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private WebhookResponse toResponse(Webhook hook) {
        return WebhookResponse.builder()
                .id(hook.getId())
                .url(hook.getUrl())
                .events(parseEvents(hook.getEvents()))
                .secret(hook.getSecret())
                .enabled(hook.getEnabled())
                .createdByUsername(hook.getCreatedBy().getUsername())
                .createdAt(hook.getCreatedAt())
                .build();
    }
}

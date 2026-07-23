package dev.securecdms.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class WebhookResponse {
    private Long id;
    private String url;
    private List<String> events;
    private String secret;
    private Boolean enabled;
    private String createdByUsername;
    private Instant createdAt;
}

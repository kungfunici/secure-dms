package dev.securecdms.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class WebhookRequest {
    private String url;
    private List<String> events;
    private String secret;
    private Boolean enabled;
}

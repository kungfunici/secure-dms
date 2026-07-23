package dev.securecdms.repository;

import dev.securecdms.model.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {
    List<Webhook> findByEnabledTrue();
}

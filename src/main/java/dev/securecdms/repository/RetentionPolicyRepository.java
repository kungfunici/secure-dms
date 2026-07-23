package dev.securecdms.repository;

import dev.securecdms.model.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, Long> {
    List<RetentionPolicy> findByEnabledTrue();
}

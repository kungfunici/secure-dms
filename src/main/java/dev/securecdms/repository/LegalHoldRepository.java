package dev.securecdms.repository;

import dev.securecdms.model.Document;
import dev.securecdms.model.LegalHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LegalHoldRepository extends JpaRepository<LegalHold, Long> {
    Optional<LegalHold> findByDocumentAndReleasedAtIsNull(Document document);
    List<LegalHold> findByReleasedAtIsNull();
    boolean existsByDocumentAndReleasedAtIsNull(Document document);
}

package dev.securecdms.repository;

import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentOrderByVersionNumberDesc(Document document);

    Optional<DocumentVersion> findByDocumentAndVersionNumber(Document document, Integer versionNumber);

    int countByDocument(Document document);

    List<DocumentVersion> findByDocumentAndCreatedAtBefore(Document document, Instant cutoff);
}

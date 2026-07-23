package dev.securecdms.repository;

import dev.securecdms.model.DocumentKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentKeyRepository extends JpaRepository<DocumentKey, Long> {
    List<DocumentKey> findByDocumentId(Long documentId);
    Optional<DocumentKey> findByDocumentIdAndUserId(Long documentId, Long userId);
    void deleteByDocumentId(Long documentId);
}

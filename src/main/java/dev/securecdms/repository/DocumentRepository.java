package dev.securecdms.repository;

import dev.securecdms.model.Document;
import dev.securecdms.model.Folder;
import dev.securecdms.model.Tag;
import dev.securecdms.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("""
        SELECT d FROM Document d
        WHERE d.owner = :owner AND d.deletedAt IS NULL
        """)
    Page<Document> findByOwner(@Param("owner") User owner, Pageable pageable);

    @Query("""
        SELECT d FROM Document d
        WHERE d.owner = :owner AND d.deletedAt IS NOT NULL
        """)
    Page<Document> findTrashByOwner(@Param("owner") User owner, Pageable pageable);

    @Query("""
        SELECT d FROM Document d
        WHERE d.owner = :owner AND d.deletedAt IS NOT NULL
          AND (LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Document> searchTrashByOwner(@Param("owner") User owner,
                                       @Param("query") String query,
                                       Pageable pageable);

    List<Document> findByOwnerAndFolder(User owner, Folder folder);

    Optional<Document> findByOwnerAndOriginalFilename(User owner, String originalFilename);

    List<Document> findByOwnerAndFolderIsNullAndDeletedAtIsNull(User owner);

    @Query("""
        SELECT d FROM Document d
        WHERE d.owner = :owner
          AND d.deletedAt IS NULL
          AND (LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Document> searchByOwner(@Param("owner") User owner,
                                   @Param("query") String query,
                                   Pageable pageable);

    @Query("""
        SELECT d FROM Document d
        JOIN d.permissions p
        WHERE p.user = :user AND d.deletedAt IS NULL
        """)
    Page<Document> findSharedWithUser(@Param("user") User user, Pageable pageable);

    @Query("""
        SELECT d FROM Document d
        JOIN d.permissions p
        WHERE p.user = :user AND d.deletedAt IS NULL
          AND (LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Document> searchSharedWithUser(@Param("user") User user,
                                         @Param("query") String query,
                                         Pageable pageable);

    @Query("""
        SELECT d FROM Document d
        JOIN d.permissions p
        WHERE p.user = :user AND d.deletedAt IS NULL
        """)
    List<Document> findSharedWithUserList(@Param("user") User user);

    @Query("""
        SELECT DISTINCT d FROM Document d
        WHERE d.owner = :owner
          AND d.deletedAt IS NULL
          AND d.permissions IS NOT EMPTY
        """)
    Page<Document> findSharedByOwner(@Param("owner") User owner, Pageable pageable);

    @Query("""
        SELECT DISTINCT d FROM Document d
        WHERE d.owner = :owner
          AND d.deletedAt IS NULL
          AND d.permissions IS NOT EMPTY
          AND (LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Document> searchSharedByOwner(@Param("owner") User owner,
                                        @Param("query") String query,
                                        Pageable pageable);

    @Query("""
        SELECT DISTINCT d FROM Document d
        LEFT JOIN d.permissions p
        WHERE (d.owner = :user OR p.user = :user)
          AND d.deletedAt IS NULL
          AND (LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.extractedText) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Document> searchOwnedAndShared(@Param("user") User user,
                                         @Param("query") String query,
                                         Pageable pageable);

    List<Document> findByRetentionAtBeforeAndDeletedAtIsNull(Instant retentionAt);

    @Query("SELECT d FROM Document d JOIN d.tags t WHERE t = :tag")
    List<Document> findByTagsContaining(@Param("tag") Tag tag);
}

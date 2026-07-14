package dev.securecdms.repository;

import dev.securecdms.model.Document;
import dev.securecdms.model.Folder;
import dev.securecdms.model.User;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Page<Document> findByOwner(User owner, Pageable pageable);

    List<Document> findByOwnerAndFolder(User owner, Folder folder);

    List<Document> findByOwnerAndFolderIsNull(User owner);

    @Query("""
        SELECT d FROM Document d
        WHERE d.owner = :owner
          AND (LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Document> searchByOwner(@Param("owner") User owner,
                                  @Param("query") String query,
                                  Pageable pageable);

    @Query("""
        SELECT d FROM Document d
        JOIN d.permissions p
        WHERE p.user = :user
        """)
    Page<Document> findSharedWithUser(@Param("user") User user, Pageable pageable);

    @Query("""
        SELECT d FROM Document d
        JOIN d.permissions p
        WHERE p.user = :user
        """)
    List<Document> findSharedWithUserList(@Param("user") User user);

    @Query("""
        SELECT DISTINCT d FROM Document d
        WHERE d.owner = :owner
          AND d.permissions IS NOT EMPTY
        """)
    Page<Document> findSharedByOwner(@Param("owner") User owner, Pageable pageable);

    @Query("""
        SELECT DISTINCT d FROM Document d
        LEFT JOIN d.permissions p
        WHERE (d.owner = :user OR p.user = :user)
          AND (LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :query, '%'))
               OR LOWER(d.description) LIKE LOWER(CONCAT('%', :query, '%')))
        """)
    Page<Document> searchOwnedAndShared(@Param("user") User user,
                                        @Param("query") String query,
                                        Pageable pageable);
}

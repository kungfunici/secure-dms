package dev.securecdms.repository;

import dev.securecdms.model.Favorite;
import dev.securecdms.model.User;
import dev.securecdms.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserAndDocument(User user, Document document);

    boolean existsByUserAndDocument(User user, Document document);

    List<Favorite> findByUserOrderByCreatedAtDesc(User user);

    void deleteByUserAndDocument(User user, Document document);
}

package dev.securecdms.repository;

import dev.securecdms.model.RecentlyViewed;
import dev.securecdms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecentlyViewedRepository extends JpaRepository<RecentlyViewed, Long> {

    List<RecentlyViewed> findByUserOrderByViewedAtDesc(User user);

    Optional<RecentlyViewed> findByUserIdAndDocumentId(Long userId, Long documentId);
}

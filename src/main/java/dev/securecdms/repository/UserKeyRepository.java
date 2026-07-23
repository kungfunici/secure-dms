package dev.securecdms.repository;

import dev.securecdms.model.UserKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserKeyRepository extends JpaRepository<UserKey, Long> {
    Optional<UserKey> findByUserId(Long userId);
    boolean existsByUserId(Long userId);
}

package dev.securecdms.repository;

import dev.securecdms.model.Folder;
import dev.securecdms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByOwnerOrderByName(User owner);
    List<Folder> findByOwnerIdOrderByName(Long ownerId);
}

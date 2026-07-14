package dev.securecdms.service;

import dev.securecdms.dto.response.UserResponse;
import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = findUser(userId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, String username, MultipartFile avatar) throws IOException {
        User user = findUser(userId);

        if (!user.getId().equals(userId)) {
            throw new AccessDeniedException("Cannot update another user's profile");
        }

        if (avatar != null && !avatar.isEmpty()) {
            if (user.getProfilePicture() != null) {
                storageService.delete(user.getProfilePicture());
            }
            String ext = getExtension(avatar.getOriginalFilename());
            String filename = "profile-" + userId + "-" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
            storageService.storeWithName(avatar, filename);
            user.setProfilePicture(filename);
        }

        userRepository.save(user);
        return toResponse(user);
    }

    @Transactional
    public void deleteAccount(Long userId, String username) {
        User user = findUser(userId);

        if (!user.getUsername().equals(username)) {
            throw new AccessDeniedException("Cannot delete another user's account");
        }

        List<Document> sharedDocs = documentRepository.findSharedWithUserList(user);
        for (Document doc : sharedDocs) {
            doc.getPermissions().removeIf(p -> p.getUser().getId().equals(user.getId()));
        }
        documentRepository.saveAll(sharedDocs);

        for (Document doc : user.getDocuments()) {
            try {
                storageService.delete(doc.getStoredFilename());
            } catch (IOException e) {
                log.warn("Failed to delete file: {}", doc.getStoredFilename(), e);
            }
        }

        if (user.getProfilePicture() != null) {
            try {
                storageService.delete(user.getProfilePicture());
            } catch (IOException e) {
                log.warn("Failed to delete profile picture", e);
            }
        }

        userRepository.delete(user);
        log.info("Account deleted: {}", username);
    }

    public String getAvatarPath(Long userId) {
        User user = findUser(userId);
        return user.getProfilePicture();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(String query) {
        return userRepository.searchByUsername(query).stream()
                .map(this::toResponse)
                .toList();
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .profilePicture(user.getProfilePicture())
                .createdAt(user.getCreatedAt())
                .build();
    }
}

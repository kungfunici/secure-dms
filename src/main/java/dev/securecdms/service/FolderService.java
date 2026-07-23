package dev.securecdms.service;

import dev.securecdms.dto.request.FolderRequest;
import dev.securecdms.dto.response.FolderResponse;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.Folder;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.FolderRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional
    public FolderResponse create(FolderRequest request, String username) {
        User owner = getUser(username);
        Folder folder = Folder.builder()
                .name(request.getName().trim())
                .owner(owner)
                .build();
        folderRepository.save(folder);
        log.info("Folder created: {} by {}", folder.getName(), username);
        return toResponse(folder);
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> list(String username) {
        User owner = getUser(username);
        return folderRepository.findByOwnerOrderByName(owner).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FolderResponse rename(Long folderId, FolderRequest request, String username) {
        Folder folder = getFolder(folderId);
        verifyOwner(folder, username);
        folder.setName(request.getName().trim());
        folderRepository.save(folder);
        return toResponse(folder);
    }

    @Transactional
    public void delete(Long folderId, String username) {
        Folder folder = getFolder(folderId);
        verifyOwner(folder, username);
        for (Document doc : folder.getDocuments()) {
            doc.setFolder(null);
        }
        folderRepository.delete(folder);
        log.info("Folder deleted: {} by {}", folder.getName(), username);
    }

    private Folder getFolder(Long id) {
        return folderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + id));
    }

    private void verifyOwner(Folder folder, String username) {
        if (!folder.getOwner().getUsername().equals(username)) {
            throw new dev.securecdms.exception.AccessDeniedException("Access denied");
        }
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private FolderResponse toResponse(Folder folder) {
        return FolderResponse.builder()
                .id(folder.getId())
                .name(folder.getName())
                .documentCount(folder.getDocuments().stream().filter(d -> d.getDeletedAt() == null).count())
                .createdAt(folder.getCreatedAt())
                .build();
    }
}

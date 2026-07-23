package dev.securecdms.service;

import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.DocumentKey;
import dev.securecdms.model.User;
import dev.securecdms.model.UserKey;
import dev.securecdms.repository.DocumentKeyRepository;
import dev.securecdms.repository.UserKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoService {

    private final UserKeyRepository userKeyRepository;
    private final DocumentKeyRepository documentKeyRepository;

    @Transactional
    public void storePublicKey(User user, String publicKey, String algorithm) {
        UserKey userKey = userKeyRepository.findByUserId(user.getId())
                .map(existing -> {
                    existing.setPublicKey(publicKey);
                    existing.setKeyAlgorithm(algorithm);
                    return existing;
                })
                .orElse(UserKey.builder()
                        .user(user)
                        .publicKey(publicKey)
                        .keyAlgorithm(algorithm)
                        .build());

        userKeyRepository.save(userKey);
        log.info("Public key stored for user: {}", user.getUsername());
    }

    public String getPublicKey(Long userId) {
        return userKeyRepository.findByUserId(userId)
                .map(UserKey::getPublicKey)
                .orElseThrow(() -> new ResourceNotFoundException("No public key for user: " + userId));
    }

    public boolean hasPublicKey(Long userId) {
        return userKeyRepository.existsByUserId(userId);
    }

    public void storeWrappedKey(Long documentId, Long userId, byte[] wrappedKey) {
        var docKey = DocumentKey.builder()
                .document(Document.builder().id(documentId).build())
                .user(User.builder().id(userId).build())
                .wrappedKey(wrappedKey)
                .build();
        documentKeyRepository.save(docKey);
    }

    public byte[] getWrappedKey(Long documentId, Long userId) {
        return documentKeyRepository.findByDocumentIdAndUserId(documentId, userId)
                .map(DocumentKey::getWrappedKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No key for document " + documentId + " and user " + userId));
    }

    public boolean hasAccessToKey(Long documentId, Long userId) {
        return documentKeyRepository.findByDocumentIdAndUserId(documentId, userId).isPresent();
    }

    @Transactional
    public void removeDocumentKeys(Long documentId) {
        documentKeyRepository.deleteByDocumentId(documentId);
    }
}

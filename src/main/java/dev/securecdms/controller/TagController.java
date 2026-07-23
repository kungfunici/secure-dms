package dev.securecdms.controller;

import dev.securecdms.dto.response.TagResponse;
import dev.securecdms.exception.AccessDeniedException;
import dev.securecdms.exception.ResourceNotFoundException;
import dev.securecdms.model.Document;
import dev.securecdms.model.Tag;
import dev.securecdms.model.User;
import dev.securecdms.repository.DocumentRepository;
import dev.securecdms.repository.TagRepository;
import dev.securecdms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    @GetMapping
    public ResponseEntity<List<TagResponse>> list() {
        return ResponseEntity.ok(tagRepository.findAll().stream()
                .map(t -> TagResponse.builder().id(t.getId()).name(t.getName()).color(t.getColor()).build())
                .toList());
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(@RequestBody Map<String, String> body,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        String name = body.get("name");
        String color = body.getOrDefault("color", "#6366f1");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (tagRepository.existsByName(name.trim())) {
            return ResponseEntity.badRequest().build();
        }
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Tag tag = tagRepository.save(Tag.builder().name(name.trim()).color(color).createdBy(user).build());
        return ResponseEntity.ok(TagResponse.builder().id(tag.getId()).name(tag.getName()).color(tag.getColor()).build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found: " + id));
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (tag.getCreatedBy() != null && !tag.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the creator can delete this tag");
        }
        List<Document> docsWithTag = documentRepository.findByTagsContaining(tag);
        for (Document doc : docsWithTag) {
            doc.getTags().remove(tag);
        }
        documentRepository.saveAll(docsWithTag);
        tagRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

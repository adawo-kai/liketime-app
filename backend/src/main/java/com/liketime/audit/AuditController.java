package com.liketime.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private static final int MAX_LIMIT = 500;

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;

    public AuditController(AuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Последние записи журнала, новые сверху. Можно отфильтровать по сущности и её id. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<AuditView> list(@RequestParam(required = false) String entity,
                                @RequestParam(required = false) Long entityId,
                                @RequestParam(defaultValue = "100") int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        return repository.search(entity, entityId, PageRequest.of(0, size)).stream()
                .map(this::view)
                .toList();
    }

    private AuditView view(AuditEntry e) {
        return new AuditView(e.getId(), e.getActor(), e.getAction(), e.getEntity(), e.getEntityId(),
                parse(e.getDetails()), e.getCreatedAt());
    }

    private JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }

    public record AuditView(Long id, String actor, String action, String entity, Long entityId,
                            JsonNode details, LocalDateTime createdAt) {
    }
}

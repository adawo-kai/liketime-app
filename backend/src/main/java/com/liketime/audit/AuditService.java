package com.liketime.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liketime.common.AppTime;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Запись в журнал аудита. Выполняется в той же транзакции, что и само изменение:
 * если изменение откатилось, записи об нём тоже не будет.
 */
@Service
public class AuditService {

    /** Подпись для действий, которые совершает не пользователь, а система или устройство */
    public static final String SYSTEM = "system";

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;
    private final AppTime time;

    public AuditService(AuditRepository repository, ObjectMapper objectMapper, AppTime time) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.time = time;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Authentication auth, String action, String entity, Long entityId, Object details) {
        record(auth != null ? auth.getName() : SYSTEM, action, entity, entityId, details);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String actor, String action, String entity, Long entityId, Object details) {
        repository.save(new AuditEntry(actor, action, entity, entityId, toJson(details), time.nowUtc()));
    }

    private String toJson(Object details) {
        if (details == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось записать подробности аудита", e);
        }
    }
}

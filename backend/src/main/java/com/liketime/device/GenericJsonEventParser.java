package com.liketime.device;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Универсальный формат, который понимает система и который отправляет симулятор:
 * <pre>{"eventId": "...", "personId": "...", "time": "2026-10-25T10:00:00+06:00", "confidence": 0.93}</pre>
 * Время обязательно с часовым поясом. confidence можно передать долей (0.93) или процентом (93).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GenericJsonEventParser implements DeviceEventParser {

    private static final int MAX_ID_LENGTH = 100;

    @Override
    public boolean supports(Device device) {
        return true;
    }

    @Override
    public NormalizedEvent parse(JsonNode payload) {
        String eventId = requiredText(payload, "eventId");
        String personId = requiredText(payload, "personId");
        String time = requiredText(payload, "time");
        LocalDateTime timeUtc;
        try {
            timeUtc = OffsetDateTime.parse(time).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw bad("Поле time должно быть датой и временем с часовым поясом, например 2026-10-25T10:00:00+06:00");
        }
        return new NormalizedEvent(eventId, personId, timeUtc, confidence(payload.get("confidence")));
    }

    private static BigDecimal confidence(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            throw bad("Поле confidence должно быть числом");
        }
        BigDecimal value = node.decimalValue();
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw bad("Поле confidence должно быть от 0 до 1 (или от 0 до 100 в процентах)");
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            value = value.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode node = payload.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw bad("Не указано поле " + field);
        }
        String text = node.asText().trim();
        if (text.length() > MAX_ID_LENGTH) {
            throw bad("Поле " + field + " длиннее " + MAX_ID_LENGTH + " символов");
        }
        return text;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

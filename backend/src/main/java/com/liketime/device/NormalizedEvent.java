package com.liketime.device;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Событие в едином виде, независимо от формата устройства.
 *
 * @param eventId    уникальный идентификатор события на устройстве
 * @param personId   идентификатор человека на устройстве
 * @param timeUtc    время события в UTC
 * @param confidence уверенность распознавания 0..1; null, если устройство её не передаёт
 */
public record NormalizedEvent(String eventId, String personId, LocalDateTime timeUtc, BigDecimal confidence) {
}

package com.liketime.device;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Разбирает тело события конкретного типа устройства в единый вид. Новый формат (например, родной
 * формат Dahua) добавляется как ещё один компонент с этим интерфейсом: остальной код не меняется.
 */
public interface DeviceEventParser {

    boolean supports(Device device);

    /** Бросает ResponseStatusException 400, если в событии нет обязательных полей */
    NormalizedEvent parse(JsonNode payload);
}

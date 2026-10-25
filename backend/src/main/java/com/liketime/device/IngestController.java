package com.liketime.device;

import com.liketime.device.DeviceEventService.IngestResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Вход для устройств и симулятора. Не требует входа пользователя: устройство подтверждает себя
 * серийным номером в адресе и секретным ключом в заголовке X-Device-Key.
 */
@RestController
@RequestMapping("/api/ingest/devices/{serial}")
public class IngestController {

    private final DeviceEventService service;

    public IngestController(DeviceEventService service) {
        this.service = service;
    }

    @PostMapping(value = "/events", consumes = "*/*")
    public IngestResult events(@PathVariable String serial,
                               @RequestHeader(value = "X-Device-Key", required = false) String key,
                               @RequestBody String body) {
        return service.ingest(serial, key, body);
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void heartbeat(@PathVariable String serial,
                          @RequestHeader(value = "X-Device-Key", required = false) String key) {
        service.heartbeat(serial, key);
    }
}

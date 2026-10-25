package com.liketime.device;

import com.liketime.device.DeviceAdminService.DeviceView;
import com.liketime.device.DeviceAdminService.PersonView;
import com.liketime.device.DeviceAdminService.UnmatchedView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Устройства: настройку (создание, смена ключа, отключение) делает только ADMIN,
 * привязку лиц к ученикам и просмотр могут ADMIN и MANAGER.
 */
@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private static final String STAFF = "hasAnyRole('ADMIN','MANAGER')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final DeviceAdminService service;

    public DeviceController(DeviceAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(STAFF)
    public List<DeviceView> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceView create(@Valid @RequestBody DeviceCreateRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize(ADMIN)
    public DeviceView update(@PathVariable Long id, @Valid @RequestBody DeviceUpdateRequest request,
                             Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(ADMIN)
    public DeviceView setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
                                Authentication authentication) {
        return service.setStatus(id, request.status(), authentication);
    }

    @GetMapping("/{id}/persons")
    @PreAuthorize(STAFF)
    public List<PersonView> persons(@PathVariable Long id) {
        return service.persons(id);
    }

    @PutMapping("/{id}/persons")
    @PreAuthorize(STAFF)
    public PersonView link(@PathVariable Long id, @Valid @RequestBody LinkRequest request,
                           Authentication authentication) {
        return service.link(id, request.externalPersonId(), request.studentId(), authentication);
    }

    @DeleteMapping("/{id}/persons/{externalPersonId}")
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable Long id, @PathVariable String externalPersonId, Authentication authentication) {
        service.unlink(id, externalPersonId, authentication);
    }

    @GetMapping("/{id}/unmatched")
    @PreAuthorize(STAFF)
    public List<UnmatchedView> unmatched(@PathVariable Long id) {
        return service.unmatched(id);
    }

    public record DeviceCreateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String model,
            @NotBlank @Size(max = 100) String serialNumber,
            @Size(max = 150) String location,
            @Size(max = 255) String host,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+") String secretRef) {
    }

    public record DeviceUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String model,
            @Size(max = 150) String location,
            @Size(max = 255) String host,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+") String secretRef) {
    }

    public record StatusRequest(@NotNull DeviceStatus status) {
    }

    public record LinkRequest(@NotBlank @Size(max = 100) String externalPersonId, @NotNull Long studentId) {
    }
}

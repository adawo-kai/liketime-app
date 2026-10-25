package com.liketime.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Кабинеты. Удаления нет: кабинет может быть указан в расписании и занятиях. */
@RestController
@RequestMapping("/api/rooms")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class RoomController {

    private final RoomRepository rooms;

    public RoomController(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RoomView> list() {
        return rooms.findAll(Sort.by("name")).stream().map(RoomView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RoomView create(@Valid @RequestBody RoomRequest request) {
        String name = request.name().trim();
        if (rooms.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Кабинет с таким названием уже есть");
        }
        return RoomView.from(rooms.save(new Room(name)));
    }

    @PutMapping("/{id}")
    @Transactional
    public RoomView rename(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        Room room = rooms.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Кабинет не найден"));
        String name = request.name().trim();
        if (rooms.existsByNameAndIdNot(name, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Кабинет с таким названием уже есть");
        }
        room.rename(name);
        return RoomView.from(room);
    }

    public record RoomRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record RoomView(Long id, String name) {
        static RoomView from(Room r) {
            return new RoomView(r.getId(), r.getName());
        }
    }
}

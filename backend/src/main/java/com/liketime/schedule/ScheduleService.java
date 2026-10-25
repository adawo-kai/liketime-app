package com.liketime.schedule;

import com.liketime.audit.AuditService;
import com.liketime.common.AccessScope;
import com.liketime.common.AppTime;
import com.liketime.common.Status;
import com.liketime.group.StudyGroup;
import com.liketime.group.StudyGroupRepository;
import com.liketime.room.RoomRepository;
import com.liketime.schedule.ScheduleController.GenerateRequest;
import com.liketime.schedule.ScheduleController.SlotRequest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Шаблоны расписания групп и автоматическое создание занятий по ним. */
@Service
@Transactional
public class ScheduleService {

    private static final int MAX_GENERATE_DAYS = 93;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final ScheduleSlotRepository slots;
    private final StudyGroupRepository groups;
    private final RoomRepository rooms;
    private final LessonRepository lessons;
    private final AccessScope access;
    private final AppTime time;
    private final AuditService audit;

    public ScheduleService(ScheduleSlotRepository slots, StudyGroupRepository groups, RoomRepository rooms,
                           LessonRepository lessons, AccessScope access, AppTime time, AuditService audit) {
        this.slots = slots;
        this.groups = groups;
        this.rooms = rooms;
        this.lessons = lessons;
        this.access = access;
        this.time = time;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SlotView> slotsOf(Long groupId, Authentication auth) {
        StudyGroup group = findGroup(groupId);
        if (!access.isStaff(auth)) {
            boolean own = access.currentTeacherId(auth).filter(t -> t.equals(group.getTeacherId())).isPresent();
            if (!own) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена");
            }
        }
        return slots.findByGroupIdOrderByWeekdayAscStartTimeAsc(groupId).stream().map(SlotView::from).toList();
    }

    public SlotView createSlot(SlotRequest r, Authentication auth) {
        StudyGroup group = findGroup(r.groupId());
        if (group.getStatus() != Status.ACTIVE) {
            throw conflict("Нельзя менять расписание архивной группы");
        }
        validate(r);
        if (slots.existsByGroupIdAndWeekdayAndStartTime(r.groupId(), r.weekday(), r.startTime())) {
            throw conflict("В это время у группы уже есть занятие по расписанию");
        }
        ScheduleSlot saved = slots.save(
                new ScheduleSlot(r.groupId(), r.weekday(), r.startTime(), r.endTime(), r.roomId()));
        audit.record(auth, "SLOT_CREATE", "slot", saved.getId(), describe(saved));
        return SlotView.from(saved);
    }

    public SlotView updateSlot(Long id, SlotRequest r, Authentication auth) {
        ScheduleSlot slot = findSlot(id);
        validate(r);
        if (slots.existsByGroupIdAndWeekdayAndStartTimeAndIdNot(slot.getGroupId(), r.weekday(), r.startTime(), id)) {
            throw conflict("В это время у группы уже есть занятие по расписанию");
        }
        slot.update(r.weekday(), r.startTime(), r.endTime(), r.roomId());
        audit.record(auth, "SLOT_UPDATE", "slot", id, describe(slot));
        return SlotView.from(slot);
    }

    public void setActive(Long id, boolean active, Authentication auth) {
        ScheduleSlot slot = findSlot(id);
        if (slot.isActive() == active) {
            return;
        }
        slot.setActive(active);
        audit.record(auth, active ? "SLOT_ACTIVATE" : "SLOT_DEACTIVATE", "slot", id, describe(slot));
    }

    /**
     * Создаёт занятия по активным шаблонам групп на период. Уже существующие занятия пропускаются,
     * поэтому операцию безопасно повторять. Если преподаватель или кабинет заняты, занятие не создаётся,
     * а причина попадает в список conflicts.
     */
    public GenerateResult generate(GenerateRequest r, Authentication auth) {
        if (r.to().isBefore(r.from())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата окончания раньше даты начала");
        }
        if (ChronoUnit.DAYS.between(r.from(), r.to()) + 1 > MAX_GENERATE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Можно создавать занятия не более чем на " + MAX_GENERATE_DAYS + " дней за раз");
        }
        List<StudyGroup> targets = r.groupId() != null
                ? List.of(findGroup(r.groupId()))
                : groups.findAll().stream().filter(g -> g.getStatus() == Status.ACTIVE).toList();

        int created = 0;
        int existing = 0;
        List<String> conflicts = new ArrayList<>();
        for (StudyGroup group : targets) {
            if (group.getStatus() != Status.ACTIVE) {
                continue;
            }
            List<ScheduleSlot> active = slots.findByGroupIdAndActiveTrue(group.getId());
            for (LocalDate date = r.from(); !date.isAfter(r.to()); date = date.plusDays(1)) {
                DayOfWeek day = date.getDayOfWeek();
                for (ScheduleSlot slot : active) {
                    if (slot.getWeekday() != day.getValue()) {
                        continue;
                    }
                    LocalDateTime startsAt = time.toUtc(date, slot.getStartTime());
                    LocalDateTime endsAt = time.toUtc(date, slot.getEndTime());
                    if (lessons.existsByGroupIdAndStartsAt(group.getId(), startsAt)) {
                        existing++;
                        continue;
                    }
                    String when = group.getName() + ", " + date.atTime(slot.getStartTime()).format(WHEN);
                    Long teacherId = group.getTeacherId();
                    if (teacherId != null && lessons.teacherBusy(teacherId, startsAt, endsAt, null)) {
                        conflicts.add(when + ": преподаватель занят");
                        continue;
                    }
                    if (slot.getRoomId() != null && lessons.roomBusy(slot.getRoomId(), startsAt, endsAt, null)) {
                        conflicts.add(when + ": кабинет занят");
                        continue;
                    }
                    lessons.save(new Lesson(group.getId(), teacherId, slot.getRoomId(), slot.getId(),
                            startsAt, endsAt, LessonOrigin.GENERATED, null));
                    created++;
                }
            }
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", r.from().toString());
        details.put("to", r.to().toString());
        details.put("groupId", r.groupId());
        details.put("created", created);
        details.put("conflicts", conflicts.size());
        audit.record(auth, "LESSONS_GENERATED", "schedule", null, details);
        return new GenerateResult(created, existing, conflicts);
    }

    private void validate(SlotRequest r) {
        if (!r.endTime().isAfter(r.startTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Время окончания должно быть позже начала");
        }
        if (r.roomId() != null && !rooms.existsById(r.roomId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Кабинет не найден");
        }
    }

    private StudyGroup findGroup(Long id) {
        return groups.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена"));
    }

    private ScheduleSlot findSlot(Long id) {
        return slots.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Строка расписания не найдена"));
    }

    private static Map<String, Object> describe(ScheduleSlot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groupId", s.getGroupId());
        m.put("weekday", s.getWeekday());
        m.put("start", s.getStartTime().toString());
        m.put("end", s.getEndTime().toString());
        m.put("roomId", s.getRoomId());
        m.put("active", s.isActive());
        return m;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record SlotView(Long id, Long groupId, int weekday, LocalTime startTime, LocalTime endTime,
                           Long roomId, boolean active) {
        static SlotView from(ScheduleSlot s) {
            return new SlotView(s.getId(), s.getGroupId(), s.getWeekday(), s.getStartTime(), s.getEndTime(),
                    s.getRoomId(), s.isActive());
        }
    }

    public record GenerateResult(int created, int alreadyExisted, List<String> conflicts) {
    }
}

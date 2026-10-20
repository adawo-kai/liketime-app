package com.liketime.teacher;

import com.liketime.auth.Role;
import com.liketime.auth.UserAccount;
import com.liketime.auth.UserAccountRepository;
import com.liketime.common.Status;
import com.liketime.group.StudyGroupRepository;
import com.liketime.teacher.TeacherController.TeacherRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TeacherService {

    private final TeacherRepository teachers;
    private final UserAccountRepository users;
    private final StudyGroupRepository groups;

    public TeacherService(TeacherRepository teachers, UserAccountRepository users, StudyGroupRepository groups) {
        this.teachers = teachers;
        this.users = users;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public List<TeacherView> list(Status status) {
        Sort sort = Sort.by("fullName");
        List<Teacher> all = status != null ? teachers.findByStatus(status, sort) : teachers.findAll(sort);
        return all.stream().map(TeacherView::from).toList();
    }

    @Transactional(readOnly = true)
    public TeacherView get(Long id) {
        return TeacherView.from(find(id));
    }

    public TeacherView create(TeacherRequest r) {
        checkUserLink(r.userId(), null);
        return TeacherView.from(teachers.save(new Teacher(r.userId(), r.fullName().trim(), r.phone())));
    }

    public TeacherView update(Long id, TeacherRequest r) {
        Teacher teacher = find(id);
        checkUserLink(r.userId(), teacher);
        teacher.update(r.userId(), r.fullName().trim(), r.phone());
        return TeacherView.from(teacher);
    }

    /** Мягкое удаление. Преподавателя с активными группами сначала нужно заменить в этих группах. */
    public void deactivate(Long id) {
        Teacher teacher = find(id);
        if (groups.existsByTeacherIdAndStatus(id, Status.ACTIVE)) {
            throw conflict("У преподавателя есть активные группы: назначьте им другого преподавателя");
        }
        teacher.setStatus(Status.INACTIVE);
    }

    public TeacherView activate(Long id) {
        Teacher teacher = find(id);
        teacher.setStatus(Status.ACTIVE);
        return TeacherView.from(teacher);
    }

    /** Привязать можно только существующую учётную запись с ролью TEACHER, свободную от другого преподавателя */
    private void checkUserLink(Long userId, Teacher current) {
        if (userId == null) {
            return;
        }
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пользователь не найден"));
        if (user.getRole() != Role.TEACHER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "К преподавателю можно привязать только пользователя с ролью TEACHER");
        }
        boolean takenByOther = teachers.findByUserId(userId)
                .filter(t -> current == null || !t.getId().equals(current.getId()))
                .isPresent();
        if (takenByOther) {
            throw conflict("Этот пользователь уже привязан к другому преподавателю");
        }
    }

    private Teacher find(Long id) {
        return teachers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Преподаватель не найден"));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record TeacherView(Long id, Long userId, String fullName, String phone, Status status,
                              LocalDateTime createdAt) {
        public static TeacherView from(Teacher t) {
            return new TeacherView(t.getId(), t.getUserId(), t.getFullName(), t.getPhone(), t.getStatus(),
                    t.getCreatedAt());
        }
    }
}

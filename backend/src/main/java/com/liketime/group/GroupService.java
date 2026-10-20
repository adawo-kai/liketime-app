package com.liketime.group;

import com.liketime.auth.UserAccountRepository;
import com.liketime.common.Status;
import com.liketime.group.GroupController.AddStudentRequest;
import com.liketime.group.GroupController.GroupRequest;
import com.liketime.student.Student;
import com.liketime.student.StudentRepository;
import com.liketime.teacher.Teacher;
import com.liketime.teacher.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class GroupService {

    private final StudyGroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final UserAccountRepository users;

    public GroupService(StudyGroupRepository groups, GroupMembershipRepository memberships,
                        StudentRepository students, TeacherRepository teachers, UserAccountRepository users) {
        this.groups = groups;
        this.memberships = memberships;
        this.students = students;
        this.teachers = teachers;
        this.users = users;
    }

    /** Администратор и менеджер видят все группы, преподаватель только свои */
    @Transactional(readOnly = true)
    public List<GroupView> list(Authentication auth) {
        Sort sort = Sort.by("name");
        List<StudyGroup> found = isStaff(auth)
                ? groups.findAll(sort)
                : currentTeacherId(auth).map(id -> groups.findByTeacherId(id, sort)).orElse(List.of());
        return found.stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public GroupView get(Long id, Authentication auth) {
        return view(findAccessible(id, auth));
    }

    public GroupView create(GroupRequest r) {
        String name = r.name().trim();
        if (groups.existsByName(name)) {
            throw conflict("Группа с таким названием уже есть");
        }
        checkTeacher(r.teacherId());
        return view(groups.save(new StudyGroup(name, r.teacherId())));
    }

    public GroupView update(Long id, GroupRequest r) {
        StudyGroup group = find(id);
        String name = r.name().trim();
        if (groups.existsByNameAndIdNot(name, id)) {
            throw conflict("Группа с таким названием уже есть");
        }
        if (r.teacherId() != null && !r.teacherId().equals(group.getTeacherId())) {
            checkTeacher(r.teacherId());
        }
        group.update(name, r.teacherId());
        return view(group);
    }

    /** Мягкое удаление: группа архивируется, все ученики выходят из неё сегодняшним днём. */
    public void archive(Long id) {
        StudyGroup group = find(id);
        if (group.getStatus() == Status.INACTIVE) {
            return;
        }
        LocalDate today = today();
        memberships.findByGroupIdAndLeftOnIsNull(id).forEach(m -> m.leave(today));
        group.setStatus(Status.INACTIVE);
    }

    public GroupView restore(Long id) {
        StudyGroup group = find(id);
        group.setStatus(Status.ACTIVE);
        return view(group);
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(Long groupId, boolean activeOnly, Authentication auth) {
        findAccessible(groupId, auth);
        List<GroupMembership> found = activeOnly
                ? memberships.findByGroupIdAndLeftOnIsNull(groupId)
                : memberships.findByGroupIdOrderByJoinedOnAscIdAsc(groupId);
        Map<Long, Student> byId = students.findAllById(found.stream().map(GroupMembership::getStudentId).toList())
                .stream().collect(Collectors.toMap(Student::getId, Function.identity()));
        return found.stream()
                .map(m -> MemberView.from(m, byId.get(m.getStudentId())))
                .sorted(Comparator.comparing(MemberView::fullName).thenComparing(MemberView::joinedOn))
                .toList();
    }

    public MemberView addStudent(Long groupId, AddStudentRequest r) {
        StudyGroup group = find(groupId);
        if (group.getStatus() != Status.ACTIVE) {
            throw conflict("Нельзя добавлять учеников в архивную группу");
        }
        Student student = students.findById(r.studentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ученик не найден"));
        if (student.getStatus() != Status.ACTIVE) {
            throw conflict("Нельзя добавить неактивного ученика");
        }
        if (memberships.findByGroupIdAndStudentIdAndLeftOnIsNull(groupId, student.getId()).isPresent()) {
            throw conflict("Ученик уже состоит в этой группе");
        }
        LocalDate joinedOn = r.joinedOn() != null ? r.joinedOn() : today();
        GroupMembership saved = memberships.save(new GroupMembership(groupId, student.getId(), joinedOn));
        return MemberView.from(saved, student);
    }

    public void removeStudent(Long groupId, Long studentId) {
        find(groupId);
        GroupMembership membership = memberships.findByGroupIdAndStudentIdAndLeftOnIsNull(groupId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ученик не состоит в этой группе"));
        membership.leave(today());
    }

    private StudyGroup find(Long id) {
        return groups.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена"));
    }

    /** Чужая группа для преподавателя выглядит как несуществующая, чтобы не раскрывать её наличие */
    private StudyGroup findAccessible(Long id, Authentication auth) {
        StudyGroup group = find(id);
        if (isStaff(auth)) {
            return group;
        }
        boolean own = currentTeacherId(auth).filter(t -> t.equals(group.getTeacherId())).isPresent();
        if (!own) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Группа не найдена");
        }
        return group;
    }

    private void checkTeacher(Long teacherId) {
        if (teacherId == null) {
            return;
        }
        Teacher teacher = teachers.findById(teacherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Преподаватель не найден"));
        if (teacher.getStatus() != Status.ACTIVE) {
            throw conflict("Нельзя назначить неактивного преподавателя");
        }
    }

    private java.util.Optional<Long> currentTeacherId(Authentication auth) {
        return users.findByLogin(auth.getName())
                .flatMap(u -> teachers.findByUserId(u.getId()))
                .map(Teacher::getId);
    }

    private static boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_MANAGER"));
    }

    private GroupView view(StudyGroup g) {
        return GroupView.from(g, memberships.countByGroupIdAndLeftOnIsNull(g.getId()));
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record GroupView(Long id, String name, Long teacherId, Status status, long activeStudents,
                            LocalDateTime createdAt) {
        static GroupView from(StudyGroup g, long activeStudents) {
            return new GroupView(g.getId(), g.getName(), g.getTeacherId(), g.getStatus(), activeStudents,
                    g.getCreatedAt());
        }
    }

    public record MemberView(Long studentId, String fullName, LocalDate joinedOn, LocalDate leftOn) {
        static MemberView from(GroupMembership m, Student s) {
            return new MemberView(m.getStudentId(), s.getFullName(), m.getJoinedOn(), m.getLeftOn());
        }
    }
}

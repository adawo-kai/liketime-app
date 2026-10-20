package com.liketime.student;

import com.liketime.common.Status;
import com.liketime.group.GroupMembershipRepository;
import com.liketime.student.StudentController.StudentRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class StudentService {

    private final StudentRepository students;
    private final GroupMembershipRepository memberships;

    public StudentService(StudentRepository students, GroupMembershipRepository memberships) {
        this.students = students;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public List<StudentView> list(Status status, String query) {
        Sort sort = Sort.by("fullName");
        List<Student> all = status != null ? students.findByStatus(status, sort) : students.findAll(sort);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(s -> q.isEmpty() || s.getFullName().toLowerCase(Locale.ROOT).contains(q))
                .map(StudentView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentView get(Long id) {
        return StudentView.from(find(id));
    }

    public StudentView create(StudentRequest r) {
        return StudentView.from(students.save(
                new Student(r.fullName().trim(), r.phone(), r.parentName(), r.parentPhone())));
    }

    public StudentView update(Long id, StudentRequest r) {
        Student student = find(id);
        student.update(r.fullName().trim(), r.phone(), r.parentName(), r.parentPhone());
        return StudentView.from(student);
    }

    /** Мягкое удаление: ученик становится неактивным и выходит из всех групп сегодняшним днём. */
    public void deactivate(Long id) {
        Student student = find(id);
        if (student.getStatus() == Status.INACTIVE) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        memberships.findByStudentIdAndLeftOnIsNull(id).forEach(m -> m.leave(today));
        student.setStatus(Status.INACTIVE);
    }

    public StudentView activate(Long id) {
        Student student = find(id);
        student.setStatus(Status.ACTIVE);
        return StudentView.from(student);
    }

    private Student find(Long id) {
        return students.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ученик не найден"));
    }

    public record StudentView(Long id, String fullName, String phone, String parentName, String parentPhone,
                              Status status, LocalDateTime createdAt) {
        public static StudentView from(Student s) {
            return new StudentView(s.getId(), s.getFullName(), s.getPhone(), s.getParentName(),
                    s.getParentPhone(), s.getStatus(), s.getCreatedAt());
        }
    }
}

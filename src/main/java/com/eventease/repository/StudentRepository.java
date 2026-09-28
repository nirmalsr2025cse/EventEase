package com.eventease.repository;

import com.eventease.model.Student;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Student> findByStudentId(String studentId);

    boolean existsByStudentId(String studentId);
}

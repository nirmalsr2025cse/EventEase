package com.eventease.repository;

import com.eventease.model.CollegeAdmin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollegeAdminRepository extends JpaRepository<CollegeAdmin, Long> {
    Optional<CollegeAdmin> findByEmailIgnoreCase(String email);

    Optional<CollegeAdmin> findByAdminId(String adminId);
}

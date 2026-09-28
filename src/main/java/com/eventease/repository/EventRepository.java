package com.eventease.repository;

import com.eventease.model.CampusEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<CampusEvent, Long> {
    List<CampusEvent> findAllByOrderByEventDateAsc();

    List<CampusEvent> findByCollegeAdmin_IdOrderByEventDateAsc(Long adminId);

    long countByCollegeAdmin_Id(Long adminId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from CampusEvent e where e.id = :eventId")
    Optional<CampusEvent> findByIdForUpdate(@Param("eventId") Long eventId);
}

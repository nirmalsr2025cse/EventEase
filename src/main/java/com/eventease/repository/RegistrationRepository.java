package com.eventease.repository;

import com.eventease.model.EventRegistration;
import com.eventease.model.RegistrationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRepository extends JpaRepository<EventRegistration, Long> {
    boolean existsByEvent_IdAndStudent_IdAndRegistrationStatus(
            Long eventId, Long studentId, RegistrationStatus status);

    long countByEvent_IdAndRegistrationStatus(Long eventId, RegistrationStatus status);

    long countByStudent_IdAndRegistrationStatus(Long studentId, RegistrationStatus status);

    long countByStudent_Id(Long studentId);

    long countByEvent_IdInAndRegistrationStatus(List<Long> eventIds, RegistrationStatus status);

    List<EventRegistration> findByStudent_IdOrderByRegisteredAtDesc(Long studentId);

    List<EventRegistration> findByEvent_IdOrderByRegisteredAtDesc(Long eventId);

    List<EventRegistration> findByEvent_IdAndRegistrationStatusOrderByRegisteredAtDesc(
            Long eventId, RegistrationStatus status);

    Optional<EventRegistration> findByIdAndStudent_Id(
            Long registrationId, Long studentId);

    Optional<EventRegistration> findFirstByEvent_IdAndStudent_IdOrderByRegisteredAtDesc(
            Long eventId, Long studentId);
}

package com.eventease.dto;

import com.eventease.model.RegistrationStatus;
import java.time.LocalDateTime;

public record ParticipantView(
        Long registrationId,
        String studentId,
        String name,
        String email,
        String phone,
        String department,
        Integer year,
        LocalDateTime registrationDate,
        LocalDateTime cancelledAt,
        RegistrationStatus status) {
}

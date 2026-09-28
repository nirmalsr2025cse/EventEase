package com.eventease.dto;

import com.eventease.model.RegistrationStatus;
import java.time.LocalDateTime;

public record RegistrationView(
        Long registrationId,
        Long eventId,
        String eventCode,
        String eventTitle,
        LocalDateTime eventDate,
        String venue,
        LocalDateTime registrationDate,
        LocalDateTime cancelledAt,
        RegistrationStatus status) {
}

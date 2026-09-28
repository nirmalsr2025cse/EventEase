package com.eventease.dto;

import java.time.LocalDateTime;

public record EventView(
        Long eventId,
        String eventCode,
        String title,
        String description,
        LocalDateTime eventDate,
        String venue,
        Integer maxSeats,
        long registeredCount,
        long availableSeats,
        String status,
        Long adminId) {
}

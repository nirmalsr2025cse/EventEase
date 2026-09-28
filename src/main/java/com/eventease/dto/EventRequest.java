package com.eventease.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank @Size(max = 150) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotNull @Future LocalDateTime eventDate,
        @NotBlank @Size(max = 200) String venue,
        @NotNull @Positive Integer maxSeats) {
}

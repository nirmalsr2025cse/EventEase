package com.eventease.dto;

public record StudentProfile(
        String studentId,
        String name,
        String email,
        String phone,
        String department,
        Integer year,
        String role) {
}

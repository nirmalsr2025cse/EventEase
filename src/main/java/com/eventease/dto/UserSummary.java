package com.eventease.dto;

import com.eventease.model.UserRole;

public record UserSummary(String id, String name, String email, UserRole role) {
}
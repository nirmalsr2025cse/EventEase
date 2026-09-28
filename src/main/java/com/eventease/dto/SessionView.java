package com.eventease.dto;

public record SessionView(boolean authenticated, UserSummary user) {
}

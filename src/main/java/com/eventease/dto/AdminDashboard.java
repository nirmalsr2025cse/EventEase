package com.eventease.dto;

import java.util.List;

public record AdminDashboard(
        long totalEvents,
        long openEvents,
        long closedEvents,
        long totalRegistrations,
        List<EventView> upcomingEvents) {
}

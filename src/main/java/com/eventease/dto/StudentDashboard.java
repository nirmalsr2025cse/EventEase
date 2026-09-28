package com.eventease.dto;

import java.util.List;

public record StudentDashboard(
        long totalRegistrations,
        long activeRegistrations,
        long cancelledRegistrations,
        long upcomingEvents,
        List<EventView> upcomingEventDetails) {
}

package com.eventease.controller;

import com.eventease.dto.AdminProfile;
import com.eventease.dto.AdminDashboard;
import com.eventease.dto.ApiResponse;
import com.eventease.dto.EventView;
import com.eventease.dto.ParticipantView;
import com.eventease.model.AuthenticatedUser;
import com.eventease.service.EventEaseService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final EventEaseService eventService;

    public AdminController(EventEaseService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/profile")
    public ApiResponse<AdminProfile> profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success("Profile loaded.",
                eventService.adminProfile(Long.valueOf(user.id())));
    }

    @GetMapping("/events")
    public ApiResponse<List<EventView>> events(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success("Your events loaded.",
                eventService.adminEvents(Long.valueOf(user.id())));
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboard> dashboard(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success("Dashboard loaded.",
                eventService.adminDashboard(Long.valueOf(user.id())));
    }

    @GetMapping("/events/{eventId}/participants")
    public ApiResponse<List<ParticipantView>> participants(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long eventId) {
        return ApiResponse.success("Participants loaded.",
                eventService.participants(Long.valueOf(user.id()), eventId));
    }
}

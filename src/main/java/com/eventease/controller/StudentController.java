package com.eventease.controller;

import com.eventease.dto.ApiResponse;
import com.eventease.dto.RegistrationView;
import com.eventease.dto.StudentDashboard;
import com.eventease.dto.StudentProfile;
import com.eventease.model.AuthenticatedUser;
import com.eventease.service.EventEaseService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    private final EventEaseService eventService;

    public StudentController(EventEaseService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/profile")
    public ApiResponse<StudentProfile> profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success("Profile loaded.", eventService.studentProfile(user.id()));
    }

    @GetMapping("/registrations")
    public ApiResponse<List<RegistrationView>> registrations(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success("Your registrations loaded.",
                eventService.studentRegistrations(user.id()));
    }

    @GetMapping("/dashboard")
    public ApiResponse<StudentDashboard> dashboard(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success("Dashboard loaded.", eventService.studentDashboard(user.id()));
    }
}

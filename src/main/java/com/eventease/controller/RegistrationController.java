package com.eventease.controller;

import com.eventease.dto.ApiResponse;
import com.eventease.dto.RegistrationView;
import com.eventease.model.AuthenticatedUser;
import com.eventease.service.EventEaseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {
    private final EventEaseService eventService;

    public RegistrationController(EventEaseService eventService) {
        this.eventService = eventService;
    }

    @DeleteMapping("/{registrationId}")
    public ApiResponse<RegistrationView> cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long registrationId) {
        return ApiResponse.success("Registration cancelled.",
                eventService.cancelRegistration(registrationId, user.id()));
    }
}

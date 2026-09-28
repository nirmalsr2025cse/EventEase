package com.eventease.controller;

import com.eventease.dto.ApiResponse;
import com.eventease.dto.EventRequest;
import com.eventease.dto.EventView;
import com.eventease.model.AuthenticatedUser;
import com.eventease.service.EventEaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventEaseService eventService;

    public EventController(EventEaseService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public ApiResponse<List<EventView>> listEvents() {
        return ApiResponse.success("Events loaded.", eventService.listEvents());
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventView> getEvent(@PathVariable Long eventId) {
        return ApiResponse.success("Event loaded.", eventService.getEvent(eventId));
    }

    @PostMapping
    public ApiResponse<EventView> createEvent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody EventRequest request) {
        return ApiResponse.success("Event created successfully.",
                eventService.createEvent(Long.valueOf(user.id()), request));
    }

    @PutMapping("/{eventId}")
    public ApiResponse<EventView> updateEvent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long eventId,
            @Valid @RequestBody EventRequest request) {
        return ApiResponse.success("Event updated successfully.",
                eventService.updateEvent(Long.valueOf(user.id()), eventId, request));
    }

    @PostMapping("/{eventId}/register")
    public ApiResponse<com.eventease.dto.RegistrationView> register(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long eventId) {
        return ApiResponse.success("Registration confirmed.",
                eventService.registerStudent(eventId, user.id()));
    }
}

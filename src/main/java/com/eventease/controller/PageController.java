package com.eventease.controller;

import com.eventease.dto.AdminDashboard;
import com.eventease.dto.AdminProfile;
import com.eventease.dto.EventView;
import com.eventease.dto.RegistrationView;
import com.eventease.dto.StudentDashboard;
import com.eventease.dto.StudentProfile;
import com.eventease.model.AuthenticatedUser;
import com.eventease.model.RegistrationStatus;
import com.eventease.model.UserRole;
import com.eventease.service.EventEaseService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PageController {
    private final EventEaseService eventService;

    public PageController(EventEaseService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("authenticated", user != null);
        model.addAttribute("currentRole", user == null ? null : user.role().name());
        return "index";
    }

    @GetMapping({"/login", "/auth/login"})
    public String login() {
        return "redirect:/#how-it-works";
    }

    @GetMapping({"/signup", "/auth/signup"})
    public String signup() {
        return "redirect:/#how-it-works";
    }

    @GetMapping("/student/dashboard")
    public String studentDashboard(
            @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        String redirect = requireRole(user, UserRole.STUDENT);
        if (redirect != null) {
            return redirect;
        }
        List<RegistrationView> registrations = eventService.studentRegistrations(user.id());
        StudentDashboard dashboard = eventService.studentDashboard(user.id());
        List<RegistrationView> upcomingRegistrations = registrations.stream()
                .filter(registration -> registration.status() == RegistrationStatus.REGISTERED
                        && registration.eventDate().isAfter(LocalDateTime.now()))
                .sorted(Comparator.comparing(RegistrationView::eventDate))
                .limit(5)
                .toList();
        addUser(model, user);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("registrations", registrations);
        model.addAttribute("upcomingRegistrations", upcomingRegistrations);
        return "student/dashboard";
    }

    @GetMapping("/student/events")
    public String studentEvents(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        String redirect = requireRole(user, UserRole.STUDENT);
        if (redirect != null) {
            return redirect;
        }
        List<EventView> events = eventService.listEvents();
        Set<Long> registeredEventIds = eventService.studentRegistrations(user.id()).stream()
                .filter(registration -> registration.status() == RegistrationStatus.REGISTERED)
                .map(RegistrationView::eventId)
                .collect(Collectors.toUnmodifiableSet());
        addUser(model, user);
        model.addAttribute("events", events);
        model.addAttribute("registeredEventIds", registeredEventIds);
        return "student/events";
    }

    @GetMapping("/student/events/{eventId}")
    public String studentEventDetails(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long eventId,
            Model model) {
        String redirect = requireRole(user, UserRole.STUDENT);
        if (redirect != null) {
            return redirect;
        }
        EventView event = eventService.getEvent(eventId);
        Set<Long> registeredEventIds = eventService.studentRegistrations(user.id()).stream()
                .filter(registration -> registration.status() == RegistrationStatus.REGISTERED)
                .map(RegistrationView::eventId)
                .collect(Collectors.toUnmodifiableSet());
        addUser(model, user);
        model.addAttribute("event", event);
        model.addAttribute("registeredEventIds", registeredEventIds);
        return "student/event-details";
    }

    @GetMapping("/student/registrations")
    public String studentRegistrations(
            @AuthenticationPrincipal AuthenticatedUser user, Model model) {
        String redirect = requireRole(user, UserRole.STUDENT);
        if (redirect != null) {
            return redirect;
        }
        addUser(model, user);
        model.addAttribute("registrations", eventService.studentRegistrations(user.id()));
        return "student/registrations";
    }

    @GetMapping("/student/profile")
    public String studentProfile(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        String redirect = requireRole(user, UserRole.STUDENT);
        if (redirect != null) {
            return redirect;
        }
        addUser(model, user);
        StudentProfile profile = eventService.studentProfile(user.id());
        model.addAttribute("student", profile);
        model.addAttribute("profile", profile);
        return "student/profile";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        String redirect = requireRole(user, UserRole.COLLEGE_ADMIN);
        if (redirect != null) {
            return redirect;
        }
        List<EventView> events = eventService.adminEvents(Long.valueOf(user.id()));
        AdminDashboard dashboard = eventService.adminDashboard(Long.valueOf(user.id()));
        addUser(model, user);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("events", events);
        model.addAttribute("upcomingEvents", dashboard.upcomingEvents());
        return "admin/dashboard";
    }

    @GetMapping("/admin/events")
    public String adminEvents(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        String redirect = requireRole(user, UserRole.COLLEGE_ADMIN);
        if (redirect != null) {
            return redirect;
        }
        addUser(model, user);
        model.addAttribute("events", eventService.adminEvents(Long.valueOf(user.id())));
        return "admin/events";
    }

    @GetMapping("/admin/events/create")
    public String createEvent(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        String redirect = requireRole(user, UserRole.COLLEGE_ADMIN);
        if (redirect != null) {
            return redirect;
        }
        addUser(model, user);
        return "admin/create-event";
    }

    @GetMapping("/admin/events/{eventId}")
    public String adminEventDetails(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long eventId,
            Model model) {
        String redirect = requireRole(user, UserRole.COLLEGE_ADMIN);
        if (redirect != null) {
            return redirect;
        }
        EventView event = eventService.getEvent(eventId);
        if (!event.adminId().equals(Long.valueOf(user.id()))) {
            return "redirect:/admin/events";
        }
        addUser(model, user);
        model.addAttribute("event", event);
        return "admin/event-details";
    }

    @GetMapping("/admin/events/{eventId}/edit")
    public String editEvent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long eventId,
            Model model) {
        String redirect = requireRole(user, UserRole.COLLEGE_ADMIN);
        if (redirect != null) {
            return redirect;
        }
        EventView event = eventService.getEvent(eventId);
        if (!event.adminId().equals(Long.valueOf(user.id()))) {
            return "redirect:/admin/events";
        }
        addUser(model, user);
        model.addAttribute("event", event);
        return "admin/edit-event";
    }

    @GetMapping("/admin/events/{eventId}/participants")
    public String participants(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long eventId,
            Model model) {
        String redirect = requireRole(user, UserRole.COLLEGE_ADMIN);
        if (redirect != null) {
            return redirect;
        }
        EventView event = eventService.getEvent(eventId);
        if (!event.adminId().equals(Long.valueOf(user.id()))) {
            return "redirect:/admin/events";
        }
        addUser(model, user);
        model.addAttribute("event", event);
        model.addAttribute("selectedEvent", event);
        model.addAttribute("selectedEventId", eventId);
        model.addAttribute("events", eventService.adminEvents(Long.valueOf(user.id())));
        model.addAttribute("participants",
                eventService.participants(Long.valueOf(user.id()), eventId));
        return "admin/participants";
    }

    @GetMapping("/admin/profile")
    public String adminProfile(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        String redirect = requireRole(user, UserRole.COLLEGE_ADMIN);
        if (redirect != null) {
            return redirect;
        }
        addUser(model, user);
        AdminProfile profile = eventService.adminProfile(Long.valueOf(user.id()));
        model.addAttribute("admin", profile);
        model.addAttribute("profile", profile);
        return "admin/profile";
    }

    private String requireRole(AuthenticatedUser user, UserRole requiredRole) {
        if (user == null) {
            return "redirect:/#how-it-works";
        }
        return user.role() == requiredRole
                ? null
                : user.role() == UserRole.STUDENT
                        ? "redirect:/student/dashboard"
                        : "redirect:/admin/dashboard";
    }

    private void addUser(Model model, AuthenticatedUser user) {
        model.addAttribute("user", user);
        model.addAttribute("role", user.role().name());
        model.addAttribute(user.role() == UserRole.STUDENT ? "student" : "admin", user);
    }
}

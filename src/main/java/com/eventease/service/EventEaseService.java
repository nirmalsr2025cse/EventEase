package com.eventease.service;

import com.eventease.config.ApiException;
import com.eventease.dto.AdminDashboard;
import com.eventease.dto.AdminProfile;
import com.eventease.dto.EventRequest;
import com.eventease.dto.EventView;
import com.eventease.dto.ParticipantView;
import com.eventease.dto.RegistrationView;
import com.eventease.dto.StudentDashboard;
import com.eventease.dto.StudentProfile;
import com.eventease.model.CampusEvent;
import com.eventease.model.CollegeAdmin;
import com.eventease.model.EventRegistration;
import com.eventease.model.EventRegistrationStatus;
import com.eventease.model.RegistrationStatus;
import com.eventease.model.Student;
import com.eventease.repository.CollegeAdminRepository;
import com.eventease.repository.EventRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.StudentRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventEaseService {
    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final StudentRepository studentRepository;
    private final CollegeAdminRepository adminRepository;

    public EventEaseService(
            EventRepository eventRepository,
            RegistrationRepository registrationRepository,
            StudentRepository studentRepository,
            CollegeAdminRepository adminRepository) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
    }

    @Transactional(readOnly = true)
    public List<EventView> listEvents() {
        return eventRepository.findAllByOrderByEventDateAsc().stream().map(this::eventView).toList();
    }

    @Transactional(readOnly = true)
    public EventView getEvent(Long eventId) {
        return eventView(findEvent(eventId));
    }

    @Transactional
    public EventView createEvent(Long adminId, EventRequest request) {
        CampusEvent event = new CampusEvent();
        apply(event, request);
        event.setEventCode("EVT-" + LocalDateTime.now().getYear() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        event.setCollegeAdmin(adminRepository.getReferenceById(adminId));
        event.setRegistrationStatus(EventRegistrationStatus.OPEN);
        return eventView(eventRepository.save(event));
    }

    @Transactional
    public EventView updateEvent(Long adminId, Long eventId, EventRequest request) {
        CampusEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> ApiException.notFound("Event not found."));
        verifyOwner(event, adminId);
        if (request.maxSeats() < registeredCount(eventId)) {
            throw ApiException.conflict("Maximum seats cannot be lower than active registrations.");
        }
        apply(event, request);
        if (registeredCount(eventId) < request.maxSeats()
                && event.getEventDate().isAfter(now())
                && event.getRegistrationStatus() == EventRegistrationStatus.CLOSED) {
            event.setRegistrationStatus(EventRegistrationStatus.OPEN);
        }
        return eventView(eventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public List<EventView> adminEvents(Long adminId) {
        return eventRepository.findByCollegeAdmin_IdOrderByEventDateAsc(adminId).stream()
                .map(this::eventView).toList();
    }

    @Transactional(readOnly = true)
    public List<RegistrationView> studentRegistrations(String studentId) {
        Long internalStudentId = Long.valueOf(studentId);
        return registrationRepository.findByStudent_IdOrderByRegisteredAtDesc(internalStudentId).stream()
                .map(this::registrationView).toList();
    }

    @Transactional
    public RegistrationView registerStudent(Long eventId, String studentId) {
        Long internalStudentId = Long.valueOf(studentId);
        CampusEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> ApiException.notFound("Event not found."));
        if (event.getRegistrationStatus() != EventRegistrationStatus.OPEN
                || !event.getEventDate().isAfter(now())) {
            throw ApiException.conflict("Registration for this event is closed.");
        }
        if (registrationRepository.existsByEvent_IdAndStudent_IdAndRegistrationStatus(
                eventId, internalStudentId, RegistrationStatus.REGISTERED)) {
            throw ApiException.conflict("You are already registered for this event.");
        }
        long registered = registeredCount(eventId);
        if (registered >= event.getMaxSeats()) {
            event.setRegistrationStatus(EventRegistrationStatus.CLOSED);
            eventRepository.save(event);
            throw ApiException.conflict("This event has no seats available.");
        }
        EventRegistration registration = new EventRegistration();
        registration.setEvent(event);
        registration.setStudent(studentRepository.getReferenceById(internalStudentId));
        registration.setRegisteredAt(now());
        registration.setRegistrationStatus(RegistrationStatus.REGISTERED);
        EventRegistration saved = registrationRepository.save(registration);
        if (registered + 1 >= event.getMaxSeats()) {
            event.setRegistrationStatus(EventRegistrationStatus.CLOSED);
            eventRepository.save(event);
        }
        return registrationView(saved);
    }

    @Transactional
    public RegistrationView cancelRegistration(Long registrationId, String studentId) {
        Long internalStudentId = Long.valueOf(studentId);
        EventRegistration loaded = registrationRepository.findById(registrationId)
                .orElseThrow(() -> ApiException.notFound("Registration not found."));
        CampusEvent event = eventRepository.findByIdForUpdate(loaded.getEvent().getId())
                .orElseThrow(() -> ApiException.notFound("Event not found."));
        EventRegistration registration = registrationRepository
                .findByIdAndStudent_Id(registrationId, internalStudentId)
                .orElseThrow(() -> ApiException.notFound("Registration not found."));
        if (registration.getRegistrationStatus() == RegistrationStatus.CANCELLED) {
            throw ApiException.conflict("This registration has already been cancelled.");
        }
        if (!event.getEventDate().isAfter(now())) {
            throw ApiException.conflict("Registrations cannot be cancelled after the event has started.");
        }
        long activeBeforeCancellation = registeredCount(event.getId());
        registration.setRegistrationStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(now());
        if (event.getEventDate().isAfter(now())
                && event.getMaxSeats() > activeBeforeCancellation - 1) {
            event.setRegistrationStatus(EventRegistrationStatus.OPEN);
            eventRepository.save(event);
        }
        return registrationView(registrationRepository.save(registration));
    }

    @Transactional(readOnly = true)
    public List<ParticipantView> participants(Long adminId, Long eventId) {
        verifyOwner(findEvent(eventId), adminId);
        return registrationRepository.findByEvent_IdOrderByRegisteredAtDesc(eventId).stream()
                .map(this::participantView).toList();
    }

    @Transactional(readOnly = true)
    public StudentProfile studentProfile(String studentId) {
        Student student = studentRepository.findById(Long.valueOf(studentId))
                .orElseThrow(() -> ApiException.notFound("Student account not found."));
        return new StudentProfile(student.getStudentId(), student.getName(), student.getEmail(),
                student.getPhone(), student.getDepartment(), student.getYear(), "STUDENT");
    }

    @Transactional(readOnly = true)
    public AdminProfile adminProfile(Long adminId) {
        CollegeAdmin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> ApiException.notFound("College admin account not found."));
        return new AdminProfile(admin.getId(), admin.getAdminId(), admin.getName(), admin.getEmail(),
                "COLLEGE_ADMIN");
    }

    @Transactional(readOnly = true)
    public StudentDashboard studentDashboard(String studentId) {
        List<EventRegistration> registrations =
                registrationRepository.findByStudent_IdOrderByRegisteredAtDesc(Long.valueOf(studentId));
        long active = registrations.stream()
                .filter(r -> r.getRegistrationStatus() == RegistrationStatus.REGISTERED).count();
        long cancelled = registrations.stream()
                .filter(r -> r.getRegistrationStatus() == RegistrationStatus.CANCELLED).count();
        List<CampusEvent> futureEvents = registrations.stream()
                .filter(r -> r.getRegistrationStatus() == RegistrationStatus.REGISTERED)
                .map(EventRegistration::getEvent)
                .filter(event -> event != null && event.getEventDate().isAfter(now()))
                .sorted(Comparator.comparing(CampusEvent::getEventDate))
                .toList();
        List<EventView> upcoming = futureEvents.stream().limit(5).map(this::eventView).toList();
        return new StudentDashboard(registrations.size(), active, cancelled,
                futureEvents.size(), upcoming);
    }

    @Transactional(readOnly = true)
    public AdminDashboard adminDashboard(Long adminId) {
        List<CampusEvent> events = eventRepository.findByCollegeAdmin_IdOrderByEventDateAsc(adminId);
        List<Long> eventIds = events.stream().map(CampusEvent::getId).toList();
        long totalRegistrations = eventIds.isEmpty() ? 0
                : registrationRepository.countByEvent_IdInAndRegistrationStatus(
                        eventIds, RegistrationStatus.REGISTERED);
        List<EventView> upcoming = events.stream()
                .filter(event -> event.getEventDate().isAfter(now()))
                .limit(5).map(this::eventView).toList();
        long openEvents = events.stream()
                .filter(event -> event.getRegistrationStatus() == EventRegistrationStatus.OPEN
                        && event.getEventDate().isAfter(now())
                        && registeredCount(event.getId()) < event.getMaxSeats())
                .count();
        return new AdminDashboard(events.size(), openEvents, events.size() - openEvents,
                totalRegistrations, upcoming);
    }

    private EventView eventView(CampusEvent event) {
        long registered = registeredCount(event.getId());
        long available = Math.max(0, event.getMaxSeats() - registered);
        EventRegistrationStatus effectiveStatus = event.getRegistrationStatus();
        if (!event.getEventDate().isAfter(now()) || available == 0) {
            effectiveStatus = EventRegistrationStatus.CLOSED;
        }
        return new EventView(event.getId(), event.getEventCode(), event.getTitle(),
                event.getDescription(), event.getEventDate(), event.getVenue(), event.getMaxSeats(),
                registered, available, effectiveStatus.name(), event.getCollegeAdmin().getId());
    }

    private RegistrationView registrationView(EventRegistration registration) {
        CampusEvent event = registration.getEvent();
        return new RegistrationView(registration.getId(), event.getId(), event.getEventCode(),
                event.getTitle(), event.getEventDate(), event.getVenue(),
                registration.getRegisteredAt(), registration.getCancelledAt(),
                registration.getRegistrationStatus());
    }

    private ParticipantView participantView(EventRegistration registration) {
        Student student = registration.getStudent();
        return new ParticipantView(registration.getId(), student.getStudentId(), student.getName(),
                student.getEmail(), student.getPhone(), student.getDepartment(), student.getYear(),
                registration.getRegisteredAt(), registration.getCancelledAt(),
                registration.getRegistrationStatus());
    }

    private long registeredCount(Long eventId) {
        return registrationRepository.countByEvent_IdAndRegistrationStatus(
                eventId, RegistrationStatus.REGISTERED);
    }

    private CampusEvent findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Event not found."));
    }

    private void verifyOwner(CampusEvent event, Long adminId) {
        if (!event.getCollegeAdmin().getId().equals(adminId)) {
            throw ApiException.forbidden("You can only manage events created by your admin account.");
        }
    }

    private void apply(CampusEvent event, EventRequest request) {
        event.setTitle(request.title().trim());
        event.setDescription(request.description().trim());
        event.setEventDate(request.eventDate());
        event.setVenue(request.venue().trim());
        event.setMaxSeats(request.maxSeats());
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}

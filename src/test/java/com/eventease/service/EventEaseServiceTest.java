package com.eventease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eventease.config.ApiException;
import com.eventease.model.CampusEvent;
import com.eventease.model.EventRegistrationStatus;
import com.eventease.model.RegistrationStatus;
import com.eventease.repository.CollegeAdminRepository;
import com.eventease.repository.EventRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.StudentRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class EventEaseServiceTest {
    @Mock
    private EventRepository eventRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CollegeAdminRepository adminRepository;

    @InjectMocks
    private EventEaseService eventService;

    @Test
    void registrationUsesEventLockAndRejectsDuplicateActiveRegistration() {
        CampusEvent event = event(12L, 20);
        when(eventRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(event));
        when(registrationRepository.existsByEvent_IdAndStudent_IdAndRegistrationStatus(
                12L, 123L, RegistrationStatus.REGISTERED)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> eventService.registerStudent(12L, "123"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(eventRepository).findByIdForUpdate(12L);
    }

    @Test
    void registrationDoesNotExceedEventCapacity() {
        CampusEvent event = event(12L, 1);
        when(eventRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(event));
        when(registrationRepository.existsByEvent_IdAndStudent_IdAndRegistrationStatus(
                12L, 123L, RegistrationStatus.REGISTERED)).thenReturn(false);
        when(registrationRepository.countByEvent_IdAndRegistrationStatus(
                12L, RegistrationStatus.REGISTERED))
                .thenReturn(1L);

        ApiException exception = assertThrows(ApiException.class,
                () -> eventService.registerStudent(12L, "123"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(eventRepository).findByIdForUpdate(12L);
    }

    private CampusEvent event(Long id, int maxSeats) {
        CampusEvent event = new CampusEvent();
        event.setId(id);
        event.setEventDate(LocalDateTime.now().plusDays(1));
        event.setMaxSeats(maxSeats);
        event.setRegistrationStatus(EventRegistrationStatus.OPEN);
        return event;
    }
}

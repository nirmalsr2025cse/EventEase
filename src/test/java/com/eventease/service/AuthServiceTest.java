package com.eventease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.eventease.config.ApiException;
import com.eventease.dto.LoginRequest;
import com.eventease.dto.StudentSignupRequest;
import com.eventease.model.AuthenticatedUser;
import com.eventease.model.CollegeAdmin;
import com.eventease.model.RefreshToken;
import com.eventease.model.Student;
import com.eventease.model.UserRole;
import com.eventease.repository.CollegeAdminRepository;
import com.eventease.repository.RefreshTokenRepository;
import com.eventease.repository.StudentRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CollegeAdminRepository adminRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private AuthCookieService cookieService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                studentRepository,
                adminRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenService,
                cookieService);
        org.springframework.test.util.ReflectionTestUtils.setField(authService, "refreshTokenDays", 14L);
        lenient().when(jwtTokenService.createOpaqueRefreshToken(14L)).thenReturn("opaque-refresh");
        lenient().when(jwtTokenService.createAccessToken(any(AuthenticatedUser.class))).thenReturn("access-token");
        lenient().when(jwtTokenService.createAccessTokenWithin(
                any(AuthenticatedUser.class), any(Duration.class)))
                .thenReturn(new JwtTokenService.IssuedAccessToken(
                        "access-token", Duration.ofMinutes(30)));
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void signupStoresBcryptHashRatherThanPlainPassword() {
        when(studentRepository.existsByStudentId("S-100")).thenReturn(false);
        when(studentRepository.existsByEmailIgnoreCase("student@example.edu")).thenReturn(false);
        when(studentRepository.save(any(Student.class))).thenAnswer(invocation -> {
            Student student = invocation.getArgument(0);
            student.setId(10L);
            return student;
        });
        authService.registerStudent(new StudentSignupRequest(
                "S-100", "Test Student", "student@example.edu", "plain-password",
                "plain-password", "5551234567", "Engineering", 2));

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentRepository).save(studentCaptor.capture());
        String storedPassword = studentCaptor.getValue().getPassword();
        assertNotEquals("plain-password", storedPassword);
        assertTrue(passwordEncoder.matches("plain-password", storedPassword));
        org.mockito.Mockito.verifyNoInteractions(refreshTokenRepository, cookieService);
    }

    @Test
    void loginChecksSubmittedPasswordAgainstStoredBcryptHash() {
        Student student = student(10L, passwordEncoder.encode("correct-password"));
        when(studentRepository.findByEmailIgnoreCase("student@example.edu"))
                .thenReturn(Optional.of(student));
        when(studentRepository.getReferenceById(10L)).thenReturn(student);

        AuthService.AuthSession session = authService.login(
                new LoginRequest("student@example.edu", "correct-password"));

        assertEquals("10", session.user().id());
        assertEquals(UserRole.STUDENT, session.user().role());
        verify(adminRepository).findByEmailIgnoreCase("student@example.edu");
    }

    @Test
    void loginRejectsIncorrectPassword() {
        Student student = student(10L, passwordEncoder.encode("correct-password"));
        when(studentRepository.findByEmailIgnoreCase("student@example.edu"))
                .thenReturn(Optional.of(student));

        assertThrows(ApiException.class, () -> authService.login(
                new LoginRequest("student@example.edu", "incorrect-password")));
    }

    @Test
    void loginDeterminesCollegeAdminRoleFromStoredEmail() {
        CollegeAdmin admin = new CollegeAdmin();
        admin.setId(20L);
        admin.setName("Test Admin");
        admin.setEmail("admin@example.edu");
        admin.setPassword(passwordEncoder.encode("admin-password"));
        when(adminRepository.findByEmailIgnoreCase("admin@example.edu")).thenReturn(Optional.of(admin));
        when(adminRepository.getReferenceById(20L)).thenReturn(admin);

        AuthService.AuthSession session = authService.login(
                new LoginRequest("admin@example.edu", "admin-password"));

        assertEquals("20", session.user().id());
        assertEquals(UserRole.COLLEGE_ADMIN, session.user().role());
    }

    @Test
    void loginRejectsEmailPresentInBothAccountRoles() {
        when(studentRepository.findByEmailIgnoreCase("duplicate@example.edu"))
                .thenReturn(Optional.of(student(10L, passwordEncoder.encode("student-password"))));
        CollegeAdmin admin = new CollegeAdmin();
        admin.setId(20L);
        admin.setName("Duplicate Admin");
        admin.setEmail("duplicate@example.edu");
        admin.setPassword(passwordEncoder.encode("admin-password"));
        when(adminRepository.findByEmailIgnoreCase("duplicate@example.edu")).thenReturn(Optional.of(admin));

        assertThrows(ApiException.class, () -> authService.login(
                new LoginRequest("duplicate@example.edu", "student-password")));
    }

    @Test
    void refreshingAccessTokenKeepsOriginalRefreshTokenAndExpiry() {
        String rawRefreshToken = "opaque-refresh";
        Student student = student(10L, passwordEncoder.encode("password"));
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash("stored-token-hash");
        refreshToken.setUserType(UserRole.STUDENT.name());
        refreshToken.setStudent(student);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(5));
        LocalDateTime originalExpiry = refreshToken.getExpiresAt();
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        when(jwtTokenService.isRefreshTokenValid(rawRefreshToken)).thenReturn(true);
        when(refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(any()))
                .thenReturn(Optional.of(refreshToken));
        when(studentRepository.findById(10L)).thenReturn(Optional.of(student));

        Optional<AuthenticatedUser> refreshed =
                authService.refreshFromCookie(rawRefreshToken, response);

        assertTrue(refreshed.isPresent());
        assertEquals(UserRole.STUDENT, refreshed.orElseThrow().role());
        assertEquals(originalExpiry, refreshToken.getExpiresAt());
        verify(cookieService).writeAccessToken(response, "access-token", Duration.ofMinutes(30));
        verify(cookieService, never()).write(any(), any(), any());
        verify(refreshTokenRepository, never()).revokeByTokenHash(any());
        verify(refreshTokenRepository, never()).save(any());
        verify(jwtTokenService, never()).createOpaqueRefreshToken(anyLong());
    }

    @Test
    void expiredRefreshTokenDoesNotAuthenticateUser() {
        String rawRefreshToken = "expired-refresh";
        when(jwtTokenService.isRefreshTokenValid(rawRefreshToken)).thenReturn(false);

        Optional<AuthenticatedUser> refreshed =
                authService.refreshFromCookie(rawRefreshToken,
                        org.mockito.Mockito.mock(HttpServletResponse.class));

        assertTrue(refreshed.isEmpty());
        verify(refreshTokenRepository, never()).findByTokenHashAndRevokedAtIsNull(any());
        verify(cookieService, never()).writeAccessToken(any(), any());
    }

    private Student student(Long id, String passwordHash) {
        Student student = new Student();
        student.setId(id);
        student.setName("Test Student");
        student.setEmail("student@example.edu");
        student.setPassword(passwordHash);
        return student;
    }
}

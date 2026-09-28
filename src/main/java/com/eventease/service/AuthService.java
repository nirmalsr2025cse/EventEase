package com.eventease.service;

import com.eventease.config.ApiException;
import com.eventease.dto.LoginRequest;
import com.eventease.dto.StudentSignupRequest;
import com.eventease.dto.UserSummary;
import com.eventease.model.AuthenticatedUser;
import com.eventease.model.CollegeAdmin;
import com.eventease.model.RefreshToken;
import com.eventease.model.Student;
import com.eventease.model.UserRole;
import com.eventease.repository.CollegeAdminRepository;
import com.eventease.repository.RefreshTokenRepository;
import com.eventease.repository.StudentRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final StudentRepository studentRepository;
    private final CollegeAdminRepository adminRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthCookieService cookieService;
    @Value("${eventease.jwt.refresh-token-days:14}")
    private long refreshTokenDays;

    public AuthService(
            StudentRepository studentRepository,
            CollegeAdminRepository adminRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuthCookieService cookieService) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.cookieService = cookieService;
    }

    @Transactional
    public AuthenticatedUser registerStudent(StudentSignupRequest request) {
        String studentId = request.studentId().trim();
        String email = normalizeEmail(request.email());
        if (studentRepository.existsByStudentId(studentId)) {
            throw ApiException.conflict("Student ID already exists.");
        }
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email already registered.");
        }
        Student student = new Student();
        student.setStudentId(studentId);
        student.setName(request.name().trim());
        student.setEmail(email);
        student.setPassword(passwordEncoder.encode(request.password()));
        student.setPhone(request.phone().trim());
        student.setDepartment(request.department().trim());
        student.setYear(request.year());
        studentRepository.save(student);
        return new AuthenticatedUser(
                student.getId().toString(), student.getName(), student.getEmail(), UserRole.STUDENT);
    }

    @Transactional
    public AuthSession login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        Optional<Student> studentAccount = studentRepository.findByEmailIgnoreCase(email);
        Optional<CollegeAdmin> adminAccount = adminRepository.findByEmailIgnoreCase(email);
        if (studentAccount.isPresent() == adminAccount.isPresent()) {
            throw invalidCredentials();
        }

        AuthenticatedUser user;
        if (studentAccount.isPresent()) {
            Student student = studentAccount.get();
            if (!passwordEncoder.matches(request.password(), student.getPassword())) {
                throw invalidCredentials();
            }
            user = new AuthenticatedUser(
                    student.getId().toString(), student.getName(), student.getEmail(), UserRole.STUDENT);
        } else {
            CollegeAdmin admin = adminAccount.orElseThrow(this::invalidCredentials);
            if (!passwordEncoder.matches(request.password(), admin.getPassword())) {
                throw invalidCredentials();
            }
            user = new AuthenticatedUser(
                    admin.getId().toString(), admin.getName(), admin.getEmail(), UserRole.COLLEGE_ADMIN);
        }
        return createSession(user);
    }

    @Transactional
    public Optional<AuthenticatedUser> refreshFromCookie(
            String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        if (!jwtTokenService.isRefreshTokenValid(rawRefreshToken)) {
            return Optional.empty();
        }
        String hash = hashToken(rawRefreshToken);
        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = stored.get();
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(LocalDateTime.now())) {
            refreshTokenRepository.revokeByTokenHash(hash);
            return Optional.empty();
        }
        Optional<AuthenticatedUser> user;
        if (UserRole.STUDENT.name().equals(token.getUserType()) && token.getStudent() != null) {
            user = findStudent(token.getStudent().getId());
        } else if (UserRole.COLLEGE_ADMIN.name().equals(token.getUserType())
                && token.getCollegeAdmin() != null) {
            user = findAdmin(token.getCollegeAdmin().getId());
        } else {
            user = Optional.empty();
        }
        if (user.isEmpty()) {
            refreshTokenRepository.revokeByTokenHash(hash);
            return Optional.empty();
        }
        Duration remainingRefreshLifetime =
                Duration.between(LocalDateTime.now(), token.getExpiresAt());
        if (remainingRefreshLifetime.toMillis() <= 0) {
            refreshTokenRepository.revokeByTokenHash(hash);
            return Optional.empty();
        }
        JwtTokenService.IssuedAccessToken accessToken =
                jwtTokenService.createAccessTokenWithin(user.get(), remainingRefreshLifetime);
        cookieService.writeAccessToken(response, accessToken.value(), accessToken.lifetime());
        return user;
    }

    @Transactional
    public void revokeRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.revokeByTokenHash(hashToken(rawRefreshToken));
        }
    }

    public UserSummary toSummary(AuthenticatedUser user) {
        return new UserSummary(user.id(), user.name(), user.email(), user.role());
    }

    private AuthSession createSession(AuthenticatedUser user) {
        String rawRefreshToken = jwtTokenService.createOpaqueRefreshToken(refreshTokenDays);
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setUserType(user.role().name());
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));
        if (user.role() == UserRole.STUDENT) {
            refreshToken.setStudent(studentRepository.getReferenceById(Long.valueOf(user.id())));
        } else {
            refreshToken.setCollegeAdmin(adminRepository.getReferenceById(Long.valueOf(user.id())));
        }
        refreshTokenRepository.save(refreshToken);
        return new AuthSession(user, jwtTokenService.createAccessToken(user), rawRefreshToken);
    }

    private Optional<AuthenticatedUser> findStudent(Long id) {
        return studentRepository.findById(id).map(student ->
                new AuthenticatedUser(id.toString(), student.getName(), student.getEmail(), UserRole.STUDENT));
    }

    private Optional<AuthenticatedUser> findAdmin(Long id) {
        return adminRepository.findById(id).map(admin ->
                new AuthenticatedUser(id.toString(), admin.getName(), admin.getEmail(), UserRole.COLLEGE_ADMIN));
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "The email, account type, or password is incorrect.");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    public record AuthSession(AuthenticatedUser user, String accessToken, String refreshToken) {
    }
}

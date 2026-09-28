package com.eventease.controller;

import com.eventease.config.ApiException;
import com.eventease.dto.ApiResponse;
import com.eventease.dto.LoginRequest;
import com.eventease.dto.SessionView;
import com.eventease.dto.StudentSignupRequest;
import com.eventease.dto.UserSummary;
import com.eventease.model.AuthenticatedUser;
import com.eventease.service.AuthCookieService;
import com.eventease.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/student/register")
    public ApiResponse<UserSummary> registerStudent(
            @Valid @RequestBody StudentSignupRequest request) {
        AuthenticatedUser user = authService.registerStudent(request);
        return ApiResponse.success("Account created successfully. Please login to continue.",
                authService.toSummary(user));
    }

    @PostMapping("/login")
    public ApiResponse<UserSummary> login(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.AuthSession session = authService.login(request);
        cookieService.write(response, session.accessToken(), session.refreshToken());
        return ApiResponse.success("Signed in successfully.", authService.toSummary(session.user()));
    }

    @PostMapping("/student/login")
    public ApiResponse<UserSummary> studentLogin(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return login(request, response);
    }

    @PostMapping("/college-admin/login")
    public ApiResponse<UserSummary> collegeAdminLogin(
            @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return login(request, response);
    }

    @GetMapping("/session")
    public ApiResponse<SessionView> session(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return ApiResponse.success("No active session.", new SessionView(false, null));
        }
        return ApiResponse.success("Session is active.",
                new SessionView(true, authService.toSummary(user)));
    }

    @PostMapping("/refresh")
    public ApiResponse<UserSummary> refresh(
            HttpServletRequest request, HttpServletResponse response) {
        Optional<AuthenticatedUser> user = authService.refreshFromCookie(
                cookie(request, AuthCookieService.REFRESH_COOKIE), response);
        if (user.isEmpty()) {
            cookieService.clear(response);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh session is invalid or expired.");
        }
        return ApiResponse.success("Session refreshed.", authService.toSummary(user.get()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.revokeRefreshToken(cookie(request, AuthCookieService.REFRESH_COOKIE));
        cookieService.clear(response);
        return ApiResponse.success("You have been signed out.", null);
    }

    private String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}

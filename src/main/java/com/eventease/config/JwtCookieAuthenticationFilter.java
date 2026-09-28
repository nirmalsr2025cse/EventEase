package com.eventease.config;

import com.eventease.model.AuthenticatedUser;
import com.eventease.service.AuthCookieService;
import com.eventease.service.AuthService;
import com.eventease.service.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;
    private final AuthService authService;
    private final AuthCookieService cookieService;

    public JwtCookieAuthenticationFilter(
            JwtTokenService jwtTokenService,
            AuthService authService,
            AuthCookieService cookieService) {
        this.jwtTokenService = jwtTokenService;
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return StringUtils.startsWithIgnoreCase(path, "/api/auth/login")
                || StringUtils.startsWithIgnoreCase(path, "/api/auth/student/register")
                || StringUtils.startsWithIgnoreCase(path, "/api/auth/student/login")
                || StringUtils.startsWithIgnoreCase(path, "/api/auth/college-admin/login")
                || StringUtils.startsWithIgnoreCase(path, "/api/auth/refresh")
                || StringUtils.startsWithIgnoreCase(path, "/api/auth/logout");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String accessToken = cookie(request, AuthCookieService.ACCESS_COOKIE);
        Optional<AuthenticatedUser> user = accessToken == null
                ? Optional.empty()
                : jwtTokenService.parseAccessToken(accessToken);

        if (user.isEmpty()) {
            String refreshToken = cookie(request, AuthCookieService.REFRESH_COOKIE);
            if (refreshToken != null) {
                user = authService.refreshFromCookie(refreshToken, response);
                if (user.isEmpty()) {
                    cookieService.clear(response);
                }
            }
        }

        user.ifPresent(authenticatedUser -> SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser, null, authenticatedUser.getAuthorities())));
        filterChain.doFilter(request, response);
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

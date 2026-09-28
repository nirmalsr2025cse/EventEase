package com.eventease.service;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {
    public static final String ACCESS_COOKIE = "access_token";
    public static final String REFRESH_COOKIE = "refresh_token";

    @Value("${eventease.cookie.secure:false}")
    private boolean secure;

    @Value("${eventease.cookie.same-site:Lax}")
    private String sameSite;

    @Value("${eventease.jwt.access-token-minutes:30}")
    private long accessTokenMinutes;

    @Value("${eventease.jwt.refresh-token-days:14}")
    private long refreshTokenDays;

    public void write(HttpServletResponse response, String accessToken, String refreshToken) {
        writeAccessToken(response, accessToken);
        response.addHeader("Set-Cookie", cookie(
                REFRESH_COOKIE, "", "/api", Duration.ZERO).toString());
        response.addHeader("Set-Cookie", cookie(
                REFRESH_COOKIE, refreshToken, "/", Duration.ofDays(refreshTokenDays)).toString());
    }

    public void writeAccessToken(HttpServletResponse response, String accessToken) {
        writeAccessToken(response, accessToken, Duration.ofMinutes(accessTokenMinutes));
    }

    public void writeAccessToken(
            HttpServletResponse response, String accessToken, Duration lifetime) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.addHeader("Set-Cookie", cookie(
                ACCESS_COOKIE, accessToken, "/", lifetime).toString());
    }

    public void clear(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.addHeader("Set-Cookie", cookie(ACCESS_COOKIE, "", "/", Duration.ZERO).toString());
        response.addHeader("Set-Cookie", cookie(REFRESH_COOKIE, "", "/api", Duration.ZERO).toString());
        response.addHeader("Set-Cookie", cookie(REFRESH_COOKIE, "", "/", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .sameSite(sameSite)
                .maxAge(maxAge)
                .build();
    }
}

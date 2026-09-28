package com.eventease.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class AuthCookieServiceTest {
    @Test
    void loginCreatesRootScopedFourteenDayRefreshCookie() {
        AuthCookieService cookieService = new AuthCookieService();
        ReflectionTestUtils.setField(cookieService, "refreshTokenDays", 14L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.write(response, "access", "refresh");

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(cookie ->
                cookie.contains("refresh_token=refresh")
                        && cookie.contains("Path=/")
                        && cookie.contains("Max-Age=1209600")
                        && cookie.contains("HttpOnly")));
        assertTrue(cookies.stream().anyMatch(cookie ->
                cookie.contains("refresh_token=;")
                        && cookie.contains("Path=/api")
                        && cookie.contains("Max-Age=0")));
    }

    @Test
    void clearingSessionExpiresRefreshCookiesAtCurrentAndLegacyPaths() {
        AuthCookieService cookieService = new AuthCookieService();
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.clear(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertTrue(cookies.stream().anyMatch(cookie ->
                cookie.contains("refresh_token=;")
                        && cookie.contains("Path=/api")
                        && cookie.contains("Max-Age=0")));
        assertTrue(cookies.stream().anyMatch(cookie ->
                cookie.contains("refresh_token=;")
                        && cookie.contains("Path=/")
                        && cookie.contains("Max-Age=0")));
    }
}

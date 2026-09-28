package com.eventease.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventease.model.AuthenticatedUser;
import com.eventease.model.UserRole;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTest {
    @Test
    void renewedAccessTokenLifetimeDoesNotExceedRefreshLifetime() {
        JwtTokenService tokenService = new JwtTokenService();
        byte[] signingKey = new byte[32];
        new java.security.SecureRandom().nextBytes(signingKey);
        ReflectionTestUtils.setField(
                tokenService, "secretBase64", Base64.getEncoder().encodeToString(signingKey));
        ReflectionTestUtils.setField(tokenService, "accessTokenMinutes", 30L);
        tokenService.initialize();

        AuthenticatedUser user =
                new AuthenticatedUser("1", "Test Student", "student@example.edu", UserRole.STUDENT);
        JwtTokenService.IssuedAccessToken issued =
                tokenService.createAccessTokenWithin(user, Duration.ofSeconds(20));

        assertEquals(Duration.ofSeconds(20), issued.lifetime());
        assertTrue(tokenService.parseAccessToken(issued.value()).isPresent());
    }
}

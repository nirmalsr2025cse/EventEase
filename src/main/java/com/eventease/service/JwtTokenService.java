package com.eventease.service;

import com.eventease.model.AuthenticatedUser;
import com.eventease.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    @Value("${eventease.jwt.secret-base64:}")
    private String secretBase64;

    @Value("${eventease.jwt.access-token-minutes:30}")
    private long accessTokenMinutes;

    private SecretKey signingKey;

    @PostConstruct
    void initialize() {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET_BASE64 is missing. Add a Base64-encoded key of at least 32 bytes "
                            + "to the IntelliJ Run Configuration environment variables.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET_BASE64 must be a valid Base64-encoded key.", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET_BASE64 must decode to at least 32 bytes (256 bits).");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(AuthenticatedUser user) {
        return createAccessTokenWithin(user, Duration.ofMinutes(accessTokenMinutes)).value();
    }

    public IssuedAccessToken createAccessTokenWithin(
            AuthenticatedUser user, Duration maximumLifetime) {
        long now = System.currentTimeMillis();
        long lifetimeMillis = Math.min(
                Duration.ofMinutes(accessTokenMinutes).toMillis(), maximumLifetime.toMillis());
        if (lifetimeMillis <= 0) {
            throw new IllegalArgumentException("Access token lifetime must be positive.");
        }
        String value = Jwts.builder()
                .subject(user.id())
                .claim("name", user.name())
                .claim("email", user.email())
                .claim("role", user.role().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + lifetimeMillis))
                .signWith(signingKey)
                .compact();
        return new IssuedAccessToken(value, Duration.ofMillis(lifetimeMillis));
    }

    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(signingKey).build()
                    .parseSignedClaims(token).getPayload();
            UserRole role = UserRole.valueOf(claims.get("role", String.class));
            String id = claims.getSubject();
            String name = claims.get("name", String.class);
            String email = claims.get("email", String.class);
            if (id == null || name == null || email == null) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(id, name, email, role));
        } catch (JwtException | IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    public String createOpaqueRefreshToken(long lifetimeDays) {
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        long expiry = System.currentTimeMillis() + lifetimeDays * 86_400_000L;
        String payload = nonce + "." + expiry;
        return payload + "." + refreshSignature(payload);
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return false;
            }
            long expiry = Long.parseLong(parts[1]);
            String payload = parts[0] + "." + parts[1];
            return expiry > System.currentTimeMillis()
                    && java.security.MessageDigest.isEqual(
                            refreshSignature(payload).getBytes(StandardCharsets.US_ASCII),
                            parts[2].getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String refreshSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getEncoded(), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot sign refresh token.", exception);
        }
    }

    public record IssuedAccessToken(String value, Duration lifetime) {
    }
}

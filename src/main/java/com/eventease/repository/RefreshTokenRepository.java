package com.eventease.repository;

import com.eventease.model.RefreshToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Modifying
    @Query("update RefreshToken token set token.revokedAt = CURRENT_TIMESTAMP "
            + "where token.tokenHash = :tokenHash and token.revokedAt is null")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash);
}

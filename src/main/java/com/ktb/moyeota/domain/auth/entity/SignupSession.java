package com.ktb.moyeota.domain.auth.entity;

import com.ktb.moyeota.domain.auth.model.OAuthProvider;
import com.ktb.moyeota.domain.auth.model.OAuthUserProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "signup_sessions",
        indexes = @Index(name = "idx_signup_sessions_expires", columnList = "expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SignupSession {

    @Id
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 64)
    private String providerUserId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private SignupSession(String tokenHash, OAuthUserProfile profile, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.provider = profile.provider();
        this.providerUserId = profile.providerUserId();
        this.name = profile.name();
        this.expiresAt = expiresAt;
    }

    public static SignupSession open(String tokenHash, OAuthUserProfile profile, LocalDateTime expiresAt) {
        return new SignupSession(tokenHash, profile, expiresAt);
    }
}

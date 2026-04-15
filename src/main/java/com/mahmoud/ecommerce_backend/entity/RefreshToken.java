package com.mahmoud.ecommerce_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_rt_token_hash", columnList = "token_hash"),
                @Index(name = "idx_rt_user", columnList = "user_id"),
                @Index(name = "idx_rt_expiry", columnList = "expires_at"),
                @Index(name = "idx_rt_revoked", columnList = "revoked")
        }
)
public class RefreshToken extends BaseEntity {


    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_token_user")
    )
    private User user;


    @NotBlank
    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;


    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;


    @Builder.Default
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;


    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;


    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isActive() {
        return !revoked && (expiresAt == null || Instant.now().isBefore(expiresAt));
    }

    public void revoke(Long replacedByTokenId) {
        this.revoked = true;
        this.revokedAt = Instant.now();
        this.replacedByTokenId = replacedByTokenId;
    }
}
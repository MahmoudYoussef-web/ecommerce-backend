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
        name = "password_reset_tokens",
        indexes = {
                @Index(name = "idx_prt_token_hash", columnList = "token_hash"),
                @Index(name = "idx_prt_user", columnList = "user_id")
        }
)
public class PasswordResetToken extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_prt_user")
    )
    private User user;

    /** SHA-256 hex of the raw token. The raw value is never persisted or logged. */
    @NotBlank
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(name = "consumed", nullable = false)
    private boolean consumed = false;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public void consume() {
        this.consumed = true;
        this.consumedAt = Instant.now();
    }
}

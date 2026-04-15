package com.mahmoud.ecommerce_backend.entity;

import com.mahmoud.ecommerce_backend.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Where(clause = "is_deleted = false")
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_user_email", columnList = "email", unique = true),
                @Index(name = "idx_user_status", columnList = "status"),
                @Index(name = "idx_user_created", columnList = "created_at")
        }
)
public class User extends BaseEntity {

    @NotBlank
    @Size(min = 2, max = 50)
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @NotBlank
    @Size(min = 2, max = 50)
    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;



    @Builder.Default
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;


    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserRole> userRoles = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Cart cart;

    @Builder.Default
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Address> addresses = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Review> reviews = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Wishlist wishlist;



    @Column(name = "account_non_locked", nullable = false)
    private boolean accountNonLocked = true;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", insertable = false, updatable = false)
    private Tenant tenant;

    @Builder.Default
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId = 1L;



    public void incrementTokenVersion() {
        this.tokenVersion++;
    }

    public void addOrder(Order order) {
        if (order == null) return;
        orders.add(order);
        order.assignUser(this);
    }

    public void addAddress(Address address) {
        if (address == null) return;
        addresses.add(address);
        address.setUser(this);
    }

    public void addRole(UserRole role) {
        if (role == null) return;
        userRoles.add(role);
        role.setUser(this);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }



    public void activate() {
        if (!emailVerified) {
            throw new IllegalStateException("Cannot activate unverified email");
        }
        this.status = UserStatus.ACTIVE;
    }

    public void verifyEmail() {
        this.emailVerified = true;
        this.verificationToken = null;
    }


    @PreUpdate
    public void normalizeEmail() {
        if (email != null) {
            email = email.toLowerCase().trim();
        }
    }

    @PrePersist
    public void ensureTenant() {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalStateException("TenantId must be set");
        }
    }
}
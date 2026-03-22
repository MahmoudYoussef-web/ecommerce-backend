package com.mahmoud.ecommerce_backend.enums;

/**
 * Lifecycle states for a user account.
 *
 * PENDING_VERIFICATION - registered but email not yet confirmed.
 * ACTIVE               - fully operational account.
 * SUSPENDED            - temporarily restricted (e.g., payment dispute).
 * BANNED               - permanently restricted by an admin.
 * DEACTIVATED          - voluntarily closed by the user.
 */
public enum UserStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    BANNED,
    DEACTIVATED
}

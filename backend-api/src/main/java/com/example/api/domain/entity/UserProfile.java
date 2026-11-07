package com.example.api.domain.entity;

import java.time.Instant;
public record UserProfile(String uid, String email, String firstName, String lastName, String username,
                          String usernameNormalized, String avatar, Status status, Instant createdAt, Instant updatedAt) {
    public enum Status { PENDING, COMPLETE, DELETION_PENDING, DISABLED }
    public boolean complete() { return status == Status.COMPLETE; }
    public void requireAvailable() {
        if (status != Status.PENDING && status != Status.COMPLETE)
            throw new com.example.api.domain.exception.IdentityException(
                com.example.api.domain.exception.IdentityException.Kind.ACCOUNT_UNAVAILABLE, "Cuenta no disponible");
    }
    public boolean matches(ProfileDraft draft) {
        return complete() && java.util.Objects.equals(firstName, draft.firstName())
            && java.util.Objects.equals(lastName, draft.lastName())
            && java.util.Objects.equals(usernameNormalized, draft.usernameNormalized())
            && java.util.Objects.equals(avatar, draft.avatar());
    }
}

package com.example.api.domain.exception;

public class IdentityException extends RuntimeException {
    public enum Kind { INVALID_PROFILE, USERNAME_TAKEN, PROFILE_ALREADY_COMPLETE, PROFILE_INCOMPLETE,
        ACCOUNT_UNAVAILABLE, UNAUTHORIZED, EMAIL_DOMAIN_NOT_ALLOWED }
    private final Kind kind;
    public IdentityException(Kind kind, String message) { super(message); this.kind = kind; }
    public Kind kind() { return kind; }
    public static IdentityException unauthorized() { return new IdentityException(Kind.UNAUTHORIZED, "Sesión inválida o expirada"); }
}

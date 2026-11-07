package com.example.api.domain.entity;

import java.time.Instant;
/** Trusted identity from Firebase; never built from an HTTP request body. */
public record AuthenticatedUser(String uid, String email, String displayName, String photoUrl,
                                String provider, String tokenHash, Instant expiresAt) {}

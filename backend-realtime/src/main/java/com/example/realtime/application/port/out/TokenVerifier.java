package com.example.realtime.application.port.out;
public interface TokenVerifier {
    record Identity(String uid, long expiresAt, String tokenHash) {}
    Identity verify(String token);
    boolean active(Identity identity);
}

package com.example.realtime.application.port.out;
public interface TokenVerifier {
    record Identity(String uid, long expiresAt) {}
    Identity verify(String token);
}

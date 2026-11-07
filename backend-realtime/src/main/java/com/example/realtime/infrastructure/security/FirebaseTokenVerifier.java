package com.example.realtime.infrastructure.security;
import com.example.realtime.application.port.out.TokenVerifier;
import com.example.realtime.infrastructure.config.FirebaseProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
@ApplicationScoped
public class FirebaseTokenVerifier implements TokenVerifier {
    @Inject FirebaseProvider firebase;
    public Identity verify(String token) {
        try {
            var verified = firebase.auth().verifyIdToken(token);
            long expiration = ((Number) verified.getClaims().get("exp")).longValue() * 1000L;
            return new Identity(verified.getUid(), expiration);
        } catch (Exception e) {
            throw new SecurityException("Sesión inválida");
        }
    }
}

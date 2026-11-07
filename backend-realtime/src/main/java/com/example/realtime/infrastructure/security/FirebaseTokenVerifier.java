package com.example.realtime.infrastructure.security;

import com.example.realtime.application.port.out.TokenVerifier;
import com.example.realtime.infrastructure.config.FirebaseProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class FirebaseTokenVerifier implements TokenVerifier {
    @Inject FirebaseProvider firebase;
    public Identity verify(String token) {
        try {
            if (token == null || token.isBlank() || token.length() > 16384) throw new SecurityException("Sesión inválida");
            var verified = firebase.auth().verifyIdToken(token, true);
            var claims = (Map<?,?>) verified.getClaims().get("firebase");
            if (claims == null || !Set.of("password","google.com").contains(String.valueOf(claims.get("sign_in_provider"))))
                throw new SecurityException("Proveedor no permitido");
            long expiration = ((Number) verified.getClaims().get("exp")).longValue() * 1000L;
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
            var identity = new Identity(verified.getUid(),expiration,hash);
            if (!active(identity)) throw new SecurityException("Perfil incompleto o sesión inválida");
            return identity;
        } catch (Exception e) { throw new SecurityException("Sesión inválida o perfil incompleto"); }
    }
    public boolean active(Identity identity) {
        if (identity == null || identity.expiresAt() <= System.currentTimeMillis()) return false;
        try {
            var store = firebase.firestore();
            var profile = store.collection("users").document(identity.uid()).get().get(3, TimeUnit.SECONDS);
            var revoked = store.collection("revokedTokens").document(identity.tokenHash()).get().get(3, TimeUnit.SECONDS);
            return profile.exists() && "COMPLETE".equals(profile.getString("status")) && !revoked.exists();
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }
}

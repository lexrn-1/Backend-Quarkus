package com.example.api.infrastructure.security;

import com.example.api.application.port.out.SessionGateway;
import com.example.api.domain.entity.AuthenticatedUser;
import com.example.api.domain.exception.*;
import com.example.api.infrastructure.config.FirebaseProvider;
import com.google.firebase.auth.*;
import com.google.cloud.Timestamp;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class FirebaseSessions implements SessionGateway {
    @Inject FirebaseProvider firebase;
    public AuthenticatedUser verify(String token) {
        if (token == null || token.isBlank() || token.length() > 16384) throw IdentityException.unauthorized();
        try {
            var verified = firebase.auth().verifyIdToken(token, true);
            String hash = hash(token);
            var revoked = firebase.firestore().collection("revokedTokens").document(hash).get().get(3, TimeUnit.SECONDS);
            if (revoked.exists()) throw IdentityException.unauthorized();
            var firebaseClaims = (Map<?,?>) verified.getClaims().get("firebase");
            String provider = firebaseClaims == null ? "" : String.valueOf(firebaseClaims.get("sign_in_provider"));
            if (!Set.of("password","google.com").contains(provider)) throw IdentityException.unauthorized();
            return new AuthenticatedUser(verified.getUid(),verified.getEmail(),verified.getName(),verified.getPicture(),
                provider,hash,Instant.ofEpochSecond(((Number)verified.getClaims().get("exp")).longValue()));
        } catch (FirebaseAuthException e) {
            var code = e.getAuthErrorCode();
            if (code != null && Set.of(AuthErrorCode.EXPIRED_ID_TOKEN,AuthErrorCode.INVALID_ID_TOKEN,
                    AuthErrorCode.REVOKED_ID_TOKEN,AuthErrorCode.USER_DISABLED,AuthErrorCode.USER_NOT_FOUND).contains(code))
                throw IdentityException.unauthorized();
            throw new DependencyUnavailableException();
        } catch (IdentityException e) { throw e; }
        catch (IllegalArgumentException e) { throw IdentityException.unauthorized(); }
        catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DependencyUnavailableException();
        }
    }
    public void logout(AuthenticatedUser user) {
        try {
            // Only the current ID token is invalidated; the client must also Firebase signOut().
            firebase.firestore().collection("revokedTokens").document(user.tokenHash())
                .set(Map.of("expiresAt",Timestamp.ofTimeSecondsAndNanos(user.expiresAt().getEpochSecond(),0)))
                .get(3,TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DependencyUnavailableException();
        }
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

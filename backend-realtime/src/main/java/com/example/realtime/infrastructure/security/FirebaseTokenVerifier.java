package com.example.realtime.infrastructure.security;

import com.example.realtime.application.port.out.TokenVerifier;
import com.example.realtime.infrastructure.config.FirebaseProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class FirebaseTokenVerifier implements TokenVerifier {
  @Inject FirebaseProvider firebase;

  public Identity verify(String token) {
    var identity = verifySession(token);
    if (!active(identity)) throw new SecurityException("Sesión inválida o perfil incompleto");
    return identity;
  }

  public Identity verifySession(String token) {
    try {
      if (token == null || token.isBlank() || token.length() > 16384)
        throw new SecurityException("Sesión inválida");
      var verified = firebase.auth().verifyIdToken(token, true);
      var claims = (Map<?, ?>) verified.getClaims().get("firebase");
      if (claims == null
          || !Set.of("password", "google.com")
              .contains(String.valueOf(claims.get("sign_in_provider"))))
        throw new SecurityException("Proveedor no permitido");
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(token.getBytes(StandardCharsets.UTF_8)));
      if (firebase
          .firestore()
          .collection("revokedTokens")
          .document(hash)
          .get()
          .get(3, TimeUnit.SECONDS)
          .exists()) throw new SecurityException("Sesión cerrada");
      return new Identity(
          verified.getUid(),
          ((Number) verified.getClaims().get("exp")).longValue() * 1000L,
          hash,
          ((Number) verified.getClaims().get("auth_time")).longValue() * 1000L);
    } catch (com.google.firebase.auth.FirebaseAuthException e) {
      var code = e.getAuthErrorCode();
      if (code != null
          && Set.of(
                  com.google.firebase.auth.AuthErrorCode.INVALID_ID_TOKEN,
                  com.google.firebase.auth.AuthErrorCode.EXPIRED_ID_TOKEN,
                  com.google.firebase.auth.AuthErrorCode.REVOKED_ID_TOKEN,
                  com.google.firebase.auth.AuthErrorCode.USER_DISABLED,
                  com.google.firebase.auth.AuthErrorCode.USER_NOT_FOUND)
              .contains(code)) throw new SecurityException("Sesión inválida");
      throw new com.example.realtime.domain.collaboration.CollaborationProblem(
          "DEPENDENCY_UNAVAILABLE", "Identidad no disponible");
    } catch (SecurityException e) {
      throw e;
    } catch (IllegalArgumentException e) {
      throw new SecurityException("Sesión inválida");
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new com.example.realtime.domain.collaboration.CollaborationProblem(
          "DEPENDENCY_UNAVAILABLE", "Identidad no disponible");
    }
  }

  public boolean active(Identity identity) {
    if (identity == null || identity.expiresAt() <= System.currentTimeMillis()) return false;
    try {
      var store = firebase.firestore();
      var profile =
          store.collection("users").document(identity.uid()).get().get(3, TimeUnit.SECONDS);
      var revoked =
          store
              .collection("revokedTokens")
              .document(identity.tokenHash())
              .get()
              .get(3, TimeUnit.SECONDS);
      if (!profile.exists() || !"COMPLETE".equals(profile.getString("status")) || revoked.exists())
        return false;
      var user = firebase.auth().getUser(identity.uid());
      return !user.isDisabled()
          && user.getTokensValidAfterTimestamp() <= identity.authenticatedAt();
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return false;
    }
  }
}

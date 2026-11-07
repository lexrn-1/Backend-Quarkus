package com.example.api.infrastructure.adapter.out.persistence;

import com.example.api.application.port.out.ProfileRepository;
import com.example.api.domain.entity.*;
import com.example.api.domain.exception.*;
import com.example.api.infrastructure.config.FirebaseProvider;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.google.api.core.ApiFuture;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class FirestoreProfiles implements ProfileRepository {
    @Inject FirebaseProvider firebase;
    public Optional<UserProfile> find(String uid) {
        var doc = await(firebase.firestore().collection("users").document(uid).get());
        return doc.exists() ? Optional.of(profile(doc)) : Optional.empty();
    }
    public UserProfile complete(AuthenticatedUser user, ProfileDraft draft) {
        await(firebase.firestore().runTransaction(tx -> {
            var store = firebase.firestore();
            var userRef = store.collection("users").document(user.uid());
            var usernameRef = store.collection("usernames").document(draft.usernameNormalized());
            // All transaction reads precede all writes.
            var old = tx.get(userRef).get();
            if (old.exists()) {
                var previous = profile(old); previous.requireAvailable();
                if (previous.complete()) {
                    if (previous.matches(draft)) return true;
                    throw new IdentityException(IdentityException.Kind.PROFILE_ALREADY_COMPLETE,
                        "Perfil ya completado; la edición pertenece al flujo de perfil");
                }
            }
            var reservation = tx.get(usernameRef).get();
            if (reservation.exists() && !user.uid().equals(reservation.getString("uid")))
                throw new IdentityException(IdentityException.Kind.USERNAME_TAKEN, "El username ya está ocupado");
            var data = new HashMap<String,Object>();
            data.put("uid", user.uid()); data.put("email", user.email());
            data.put("firstName", draft.firstName()); data.put("lastName", draft.lastName());
            data.put("username", draft.username()); data.put("usernameNormalized", draft.usernameNormalized());
            data.put("avatar", draft.avatar()); data.put("status", "COMPLETE");
            data.put("createdAt", old.exists() && old.getTimestamp("createdAt") != null
                ? old.getTimestamp("createdAt") : FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            if (!reservation.exists()) tx.create(usernameRef, Map.of("uid",user.uid(),"createdAt",FieldValue.serverTimestamp()));
            tx.set(userRef, data);
            return true;
        }));
        return find(user.uid()).orElseThrow(DependencyUnavailableException::new);
    }
    private UserProfile profile(DocumentSnapshot doc) {
        try {
            return new UserProfile(doc.getId(),doc.getString("email"),doc.getString("firstName"),doc.getString("lastName"),
                doc.getString("username"),doc.getString("usernameNormalized"),doc.getString("avatar"),
                UserProfile.Status.valueOf(doc.getString("status")),instant(doc.getTimestamp("createdAt")),instant(doc.getTimestamp("updatedAt")));
        } catch (RuntimeException e) { throw new DependencyUnavailableException(); }
    }
    private Instant instant(Timestamp value) { return value == null ? null : Instant.ofEpochSecond(value.getSeconds(),value.getNanos()); }
    private <T> T await(ApiFuture<T> future) {
        try { return future.get(15,TimeUnit.SECONDS); }
        catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof IdentityException domain) throw domain;
                cause = cause.getCause();
            }
            throw new DependencyUnavailableException();
        } finally { if (!future.isDone()) future.cancel(true); }
    }
}

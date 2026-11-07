package com.example.api.infrastructure.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PreDestroy;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Date;

@ApplicationScoped
public class FirebaseProvider {
    @ConfigProperty(name="app.firebase.project-id") String project;
    @ConfigProperty(name="app.firebase.emulator-enabled") boolean emulator;
    @ConfigProperty(name="GOOGLE_APPLICATION_CREDENTIALS") java.util.Optional<String> credentialsFile;
    private FirebaseApp app;
    private Firestore store;

    void validate(@jakarta.enterprise.event.Observes io.quarkus.runtime.StartupEvent event) {
        var profiles = io.quarkus.runtime.configuration.ConfigUtils.getProfiles();
        if (emulator && !(profiles.contains("local") || profiles.contains("test")))
            throw new IllegalStateException("Emuladores requieren perfil local o test");
        if (!emulator && (System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null || System.getenv("FIRESTORE_EMULATOR_HOST") != null))
            throw new IllegalStateException("Emuladores no permitidos en producción");
    }
    public synchronized FirebaseApp app() {
        if (app != null) return app;
        boolean authEmulator = System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null;
        boolean firestoreEmulator = System.getenv("FIRESTORE_EMULATOR_HOST") != null;
        if (!emulator && (authEmulator || firestoreEmulator)) {
            throw new IllegalStateException("Emuladores no permitidos en este perfil");
        }
        if (emulator && !(authEmulator && firestoreEmulator)) {
            throw new IllegalStateException("El perfil local requiere ambos emuladores");
        }
        try {
            GoogleCredentials credentials = emulator
                ? GoogleCredentials.create(new AccessToken("owner", new Date(Long.MAX_VALUE)))
                : credentials();
            app = FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId(project)
                .setCredentials(credentials).build(), "backend-api");
            return app;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Credenciales Firebase no disponibles", e);
        }
    }
    private GoogleCredentials credentials() throws java.io.IOException {
        if (credentialsFile.isPresent()) {
            try (var stream = java.nio.file.Files.newInputStream(java.nio.file.Path.of(credentialsFile.get()))) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        return GoogleCredentials.getApplicationDefault();
    }
    public synchronized Firestore firestore() {
        if (store == null) store = FirestoreClient.getFirestore(app());
        return store;
    }
    public FirebaseAuth auth() { return FirebaseAuth.getInstance(app()); }
    @PreDestroy
    synchronized void close() throws Exception {
        if (store != null) store.close();
        if (app != null) app.delete();
    }
}

package com.example.api.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.*;
import java.util.concurrent.TimeUnit;

@Readiness
@ApplicationScoped
public class FirestoreReadiness implements io.smallrye.health.api.AsyncHealthCheck {
    @Inject FirebaseProvider firebase;
    public io.smallrye.mutiny.Uni<HealthCheckResponse> call() {
        return io.smallrye.mutiny.Uni.createFrom().item(this::check)
            .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool());
    }
    private HealthCheckResponse check() {
        try {
            // Un documento inexistente también prueba acceso; no se publica su contenido.
            firebase.firestore().document("demo/sprint0").get().get(3, TimeUnit.SECONDS);
            return HealthCheckResponse.up("firestore");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return HealthCheckResponse.down("firestore");
        }
    }
}

package com.example.api.infrastructure.adapter.out.persistence;
import com.example.api.application.port.out.DemoReader;
import com.example.api.domain.entity.Demo;
import com.example.api.domain.exception.DependencyUnavailableException;
import com.example.api.infrastructure.config.FirebaseProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
@ApplicationScoped
public class FirestoreDemoReader implements DemoReader {
    @Inject FirebaseProvider firebase;
    @ConfigProperty(name="app.demo.document") String document;
    public Optional<Demo> read() {
        try {
            var snapshot = firebase.firestore().document(document).get().get(3, TimeUnit.SECONDS);
            if (!snapshot.exists()) return Optional.empty();
            return Optional.of(new Demo(snapshot.getString("message")));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new DependencyUnavailableException();
        }
    }
}

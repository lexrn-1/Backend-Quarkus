package com.example.api.infrastructure.config;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.*;
@Liveness
@ApplicationScoped
public class RuntimeLiveness implements HealthCheck {
    public HealthCheckResponse call() { return HealthCheckResponse.up("runtime"); }
}

package com.example.realtime.infrastructure.config;
import com.example.realtime.infrastructure.adapter.in.socket.SocketServer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.*;
@Readiness @ApplicationScoped
public class SocketReadiness implements HealthCheck {
    @Inject SocketServer server;
    public HealthCheckResponse call() { return HealthCheckResponse.named("socket-listener").status(server.ready()).build(); }
}

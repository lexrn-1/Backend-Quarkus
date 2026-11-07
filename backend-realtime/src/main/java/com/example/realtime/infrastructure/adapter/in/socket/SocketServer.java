package com.example.realtime.infrastructure.adapter.in.socket;
import com.corundumstudio.socketio.*;
import com.example.realtime.application.port.out.TokenVerifier;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;

@ApplicationScoped
public class SocketServer {
    @Inject TokenVerifier tokens;
    @ConfigProperty(name="app.socket.enabled") boolean enabled;
    @ConfigProperty(name="app.socket.port") int port;
    @ConfigProperty(name="app.socket.proof-enabled") boolean proof;
    @ConfigProperty(name="app.firebase.emulator-enabled") boolean emulator;
    @ConfigProperty(name="app.socket.proof-session") Optional<String> sessions;
    @ConfigProperty(name="quarkus.http.cors.origins") List<String> origins;
    private SocketIOServer server;
    private DefaultEventExecutorGroup workers;
    private volatile boolean started;

    void start(@Observes StartupEvent event) {
        if (!emulator && (System.getenv("FIREBASE_AUTH_EMULATOR_HOST") != null || System.getenv("FIRESTORE_EMULATOR_HOST") != null))
            throw new IllegalStateException("Emuladores no permitidos en producción");
        if (proof && !emulator) throw new IllegalStateException("Prueba técnica habilitada únicamente con emuladores");
        if (!enabled) return;
        Configuration config = new Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(port);
        config.getSocketConfig().setReuseAddress(true);
        config.setBossThreads(1);
        config.setWorkerThreads(2);
        config.setMaxFramePayloadLength(16384);
        config.setMaxHttpContentLength(16384);
        config.setAuthorizationListener(handshake -> {
            String origin = handshake.getHttpHeaders().get("Origin");
            return "4".equals(handshake.getSingleUrlParam("EIO")) && origin != null && origins.contains(origin)
                ? AuthorizationResult.SUCCESSFUL_AUTHORIZATION : AuthorizationResult.FAILED_AUTHORIZATION;
        });
        server = new SocketIOServer(config);
        workers = new DefaultEventExecutorGroup(4);
        server.setPipelineFactory(new WorkerPipeline(workers));
        server.getNamespace(com.corundumstudio.socketio.namespace.Namespace.DEFAULT_NAME).addAuthTokenListener((auth, client) -> {
            try {
                if (!(auth instanceof Map<?,?> data) || !(data.get("token") instanceof String token) || token.isBlank())
                    return denied();
                var identity = tokens.verify(token);
                if (proof) {
                    Object session = data.get("proofSession");
                    if (!(session instanceof String value) || !Arrays.asList(sessions.orElse("").split(",")).contains(value) || value.isBlank())
                        return denied();
                    client.set("proofSession", value);
                }
                client.set("identity", identity);
                return AuthTokenResult.AuthTokenResultSuccess;
            } catch (Exception e) { return denied(); }
        });
        // netty-socketio calls ConnectListener during Engine.IO setup, before auth data.
        // Namespace CONNECT is guarded by WorkerPipeline; every proof event checks identity.
        if (proof) {
            server.addEventListener("proof:ping", Map.class, (client, data, ack) -> {
                if (!authorized(client)) { client.disconnect(); return; }
                ack.sendAckData(Map.of("ok", true, "data", Map.of("connectionId",client.getSessionId().toString())));
            });
            server.addEventListener("proof:signal", Map.class, (client, data, ack) -> {
                if (!authorized(client)) { client.disconnect(); return; }
                try {
                    if (data == null) { ack.sendAckData(Map.of("ok",false,"error","INVALID_SIGNAL")); return; }
                    var target = server.getClient(UUID.fromString((String)data.get("toConnectionId")));
                    String session = client.get("proofSession");
                    if (target == null || !authorized(target) || session == null || !session.equals(target.get("proofSession"))) {
                        ack.sendAckData(Map.of("ok",false,"error","FORBIDDEN")); return;
                    }
                    if (!(data.get("signal") instanceof Map<?,?> signal) || signal.isEmpty()) {
                        ack.sendAckData(Map.of("ok",false,"error","INVALID_SIGNAL")); return;
                    }
                    target.sendEvent("proof:signal",Map.of("fromConnectionId",client.getSessionId().toString(),"signal",signal));
                    ack.sendAckData(Map.of("ok",true));
                } catch (IllegalArgumentException | ClassCastException e) {
                    ack.sendAckData(Map.of("ok",false,"error","INVALID_SIGNAL"));
                }
            });
        }
        try { server.start(); started = true; }
        catch (RuntimeException e) { close(); throw e; }
    }
    private AuthTokenResult denied() { return new AuthTokenResult(false,Map.of("message","UNAUTHORIZED")); }
    private boolean authorized(SocketIOClient client) {
        TokenVerifier.Identity identity = client.get("identity");
        return identity != null && identity.expiresAt() > System.currentTimeMillis();
    }
    public boolean ready() { return started; }
    void stop(@Observes ShutdownEvent event) { close(); }
    private void close() {
        started = false;
        if (server != null) server.stop();
        if (workers != null) workers.shutdownGracefully().syncUninterruptibly();
    }
}

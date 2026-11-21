package com.example.realtime.infrastructure.adapter.in.socket;

import com.corundumstudio.socketio.*;
import com.example.realtime.application.port.in.Collaboration;
import com.example.realtime.application.port.out.TokenVerifier;
import com.example.realtime.domain.collaboration.CollaborationProblem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.*;

@ApplicationScoped
public class CollaborationSocket {
  @Inject Collaboration collaboration;
  @Inject TokenVerifier tokens;
  private SocketIOServer server;
  private ScheduledExecutorService checks;
  private final Map<String, long[]> buckets = new ConcurrentHashMap<>();
  private final Map<String, RecentSessionCheck> sessions = new ConcurrentHashMap<>();
  private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(CollaborationSocket.class);

  public void bind(SocketIOServer server) {
    this.server = server;
    for (String event :
        List.of("room:join", "room:leave", "chat:send", "chat:history", "media:update", "rtc:signal"))
      server.addEventListener(
          event, Map.class, (client, data, ack) -> handle(event, client, data, ack));
    server.addDisconnectListener(
        client -> {
          collaboration.leave(client.getSessionId().toString());
          buckets.remove(client.getSessionId().toString());
          sessions.remove(client.getSessionId().toString());
        });
    checks =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              var t = new Thread(r, "collaboration-auth-checks");
              t.setDaemon(true);
              return t;
            });
    checks.scheduleWithFixedDelay(
        () -> {
          for (var client : server.getAllClients()) {
            TokenVerifier.Identity identity = client.get("identity");
            if (identity == null)
              continue; // Engine.IO setup happens before authenticated namespace CONNECT.
            try {
              if (!tokens.active(identity)) throw new SecurityException();
              collaboration.validate(client.getSessionId().toString(), identity.uid());
            } catch (Exception e) {
              sessions.remove(client.getSessionId().toString());
              client.sendEvent("session:expired", Map.of("code", "UNAUTHORIZED"));
              collaboration.leave(client.getSessionId().toString());
              client.disconnect();
            }
          }
        },
        5,
        5,
        TimeUnit.SECONDS);
  }

  @SuppressWarnings("unchecked")
  private void handle(String event, SocketIOClient client, Map data, AckRequest ack) {
    try {
      TokenVerifier.Identity identity = client.get("identity");
      String connection = client.getSessionId().toString();
      boolean burst = event.equals("rtc:signal") || event.equals("media:update");
      if (!sessions.computeIfAbsent(connection, key -> new RecentSessionCheck())
          .active(identity, System.currentTimeMillis(), burst, () -> tokens.active(identity))) {
        client.disconnect();
        throw new CollaborationProblem("UNAUTHORIZED", "Sesión inválida");
      }
      boolean[] accepted = {true};
      long second = System.currentTimeMillis() / 1000;
      buckets.compute(
          connection,
          (key, old) -> {
            var b = old == null || old[0] != second ? new long[] {second, 0} : old;
            accepted[0] = ++b[1] <= 60;
            return b;
          });
      if (!accepted[0])
        throw new CollaborationProblem("RATE_LIMITED", "Demasiadas solicitudes; espera un momento");
      if (data == null
          || !(data.get("v") instanceof Number v)
          || v.intValue() != 1
          || !(data.get("roomId") instanceof String roomId)
          || !(data.get("payload") instanceof Map))
        throw new CollaborationProblem("INVALID_EVENT", "Evento v1 inválido");
      Map<String, Object> payload = (Map<String, Object>) data.get("payload");
      Object result =
          switch (event) {
            case "room:join" -> collaboration.join(connection, identity.uid(), roomId);
            case "room:leave" -> {
              collaboration.leave(connection);
              yield Map.of();
            }
            case "chat:send" -> {
              only(payload, Set.of("text", "clientMessageId"));
              yield collaboration.send(
                  connection,
                  identity.uid(),
                  roomId,
                  (String) payload.get("clientMessageId"),
                  (String) payload.get("text"));
            }
            case "chat:history" -> {
              only(payload, Set.of("limit", "before", "after"));
              Object limit = payload.getOrDefault("limit", 30);
              if (!(limit instanceof Number number)
                  || number.doubleValue() != number.intValue())
                throw new CollaborationProblem("INVALID_CURSOR", "Límite de historial inválido");
              yield collaboration.history(
                  identity.uid(),
                  roomId,
                  number.intValue(),
                  (String) payload.get("before"),
                  (String) payload.get("after"));
            }
            case "media:update" -> {
              only(payload, Set.of("micOn", "camOn", "screenSharing"));
              for (String flag : List.of("micOn", "camOn", "screenSharing"))
                if (!(payload.get(flag) instanceof Boolean))
                  throw new CollaborationProblem("INVALID_EVENT", "Estados de media inválidos");
              collaboration.media(
                  connection,
                  identity.uid(),
                  roomId,
                  (Boolean) payload.get("micOn"),
                  (Boolean) payload.get("camOn"),
                  (Boolean) payload.get("screenSharing"));
              yield Map.of();
            }
            case "rtc:signal" -> {
              only(payload, Set.of("toConnectionId", "signal"));
              collaboration.signal(
                  connection,
                  identity.uid(),
                  roomId,
                  (String) payload.get("toConnectionId"),
                  (Map<String, Object>) payload.get("signal"));
              yield Map.of();
            }
            default -> throw new CollaborationProblem("INVALID_EVENT", "Evento no permitido");
          };
      ack.sendAckData(Map.of("ok", true, "data", result));
    } catch (CollaborationProblem e) {
      ack.sendAckData(
          Map.of("ok", false, "error", Map.of("code", e.code(), "message", e.getMessage())));
    } catch (ClassCastException | NullPointerException e) {
      ack.sendAckData(
          Map.of(
              "ok",
              false,
              "error",
              Map.of("code", "INVALID_EVENT", "message", "Campos de evento inválidos")));
    } catch (Exception e) {
      LOG.errorf(e, "Socket operation=%s failed", event);
      ack.sendAckData(
          Map.of(
              "ok",
              false,
              "error",
              Map.of(
                  "code",
                  "DEPENDENCY_UNAVAILABLE",
                  "message",
                  "No pudimos completar la operación")));
    }
  }

  private void only(Map<String, Object> payload, Set<String> fields) {
    if (!fields.containsAll(payload.keySet()))
      throw new CollaborationProblem("INVALID_EVENT", "Campos no permitidos");
  }

  public void close() {
    if (checks != null) checks.shutdownNow();
    collaboration.close();
    buckets.clear();
    sessions.clear();
  }
}

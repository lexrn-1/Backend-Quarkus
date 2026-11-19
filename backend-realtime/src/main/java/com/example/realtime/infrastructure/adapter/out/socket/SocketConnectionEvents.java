package com.example.realtime.infrastructure.adapter.out.socket;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.realtime.application.port.out.ConnectionEvents;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class SocketConnectionEvents implements ConnectionEvents {
  private volatile SocketIOServer server;

  public void bind(SocketIOServer server) {
    this.server = server;
  }

  public void send(String id, String event, Object body) {
    var current = server;
    if (current == null) return;
    var client = current.getClient(UUID.fromString(id));
    if (client != null) client.sendEvent(event, body);
  }
}

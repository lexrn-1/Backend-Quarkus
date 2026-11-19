package com.example.realtime.infrastructure.config;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.realtime.infrastructure.adapter.out.socket.SocketConnectionEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SocketWiring {
  @Inject SocketConnectionEvents outbound;

  public void bind(SocketIOServer server) {
    outbound.bind(server);
  }
}

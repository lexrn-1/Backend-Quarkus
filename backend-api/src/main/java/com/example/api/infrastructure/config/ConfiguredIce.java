package com.example.api.infrastructure.config;

import com.example.api.application.port.in.IceConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ConfiguredIce implements IceConfiguration {
  @ConfigProperty(name = "app.ice.stun")
  String stun;

  @ConfigProperty(name = "app.ice.turn-host")
  String host;

  @ConfigProperty(name = "app.ice.turn-port")
  int port;

  @ConfigProperty(name = "app.ice.turn-username")
  Optional<String> username;

  @ConfigProperty(name = "app.ice.turn-password")
  Optional<String> password;

  public Map<String, Object> get() {
    var servers = new ArrayList<Map<String, Object>>();
    servers.add(Map.of("urls", List.of(stun)));
    boolean ready =
        username.filter(v -> !v.isBlank() && !v.matches("x+")).isPresent()
            && password.filter(v -> !v.isBlank() && !v.matches("x+")).isPresent();
    if (ready)
      servers.add(
          Map.of(
              "urls",
              List.of(
                  "turn:" + host + ":" + port + "?transport=udp",
                  "turn:" + host + ":" + port + "?transport=tcp"),
              "username",
              username.get(),
              "credential",
              password.get()));
    return Map.of("iceServers", servers, "turnConfigured", ready, "maxConnections", 6);
  }
}

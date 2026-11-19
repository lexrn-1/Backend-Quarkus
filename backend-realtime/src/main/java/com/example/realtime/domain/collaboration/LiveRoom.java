package com.example.realtime.domain.collaboration;

import java.util.*;

/**
 * Ephemeral aggregate. The application serializes changes and outbound events under one monitor.
 */
public final class LiveRoom {
  public final String id;
  public String name;
  private final Map<String, Participant> participants = new LinkedHashMap<>();

  public LiveRoom(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public void add(Participant participant) {
    if (!participants.containsKey(participant.connectionId()) && participants.size() >= 6)
      throw new CollaborationProblem("ROOM_FULL", "La sala alcanzó seis conexiones simultáneas");
    participants.putIfAbsent(participant.connectionId(), participant);
  }

  public Participant get(String connection) {
    return participants.get(connection);
  }

  public Participant remove(String connection) {
    return participants.remove(connection);
  }

  public List<Participant> snapshot() {
    return List.copyOf(participants.values());
  }

  public void media(String connection, boolean mic, boolean cam, boolean screen) {
    var p = get(connection);
    if (p == null) throw new CollaborationProblem("FORBIDDEN", "Debes entrar a la sala");
    participants.put(connection, p.media(mic, cam, screen));
  }

  public boolean empty() {
    return participants.isEmpty();
  }
}

package com.example.realtime.application.usecase;

import com.example.realtime.application.port.in.Collaboration;
import com.example.realtime.application.port.out.*;
import com.example.realtime.domain.collaboration.*;
import java.util.*;

public final class CollaborationUseCases implements Collaboration {
  private final CollaborationRepository repository;
  private final ConnectionEvents events;
  private final Map<String, LiveRoom> rooms = new HashMap<>();
  private final Map<String, String> subscriptions = new HashMap<>();
  private final Map<String, AutoCloseable> observers = new HashMap<>();

  public CollaborationUseCases(CollaborationRepository repository, ConnectionEvents events) {
    this.repository = repository;
    this.events = events;
  }

  public Map<String, Object> join(String connection, String uid, String id) {
    id = Message.roomId(id);
    var access = repository.access(uid, id);
    synchronized (this) {
      String old = subscriptions.get(connection);
      if (old != null && !old.equals(id)) leave(connection);
      var room = rooms.computeIfAbsent(id, key -> new LiveRoom(key, access.roomName()));
      boolean fresh = room.get(connection) == null;
      room.add(
          new Participant(
              connection,
              uid,
              access.self().displayName(),
              access.self().avatar(),
              false,
              false,
              false));
      subscriptions.put(connection, id);
      var snapshot =
          Map.<String, Object>of(
              "roomId",
              id,
              "connectionId",
              connection,
              "name",
              access.roomName(),
              "ownerUid",
              access.ownerUid(),
              "participants",
              room.snapshot(),
              "maxConnections",
              6);
      // Deliver snapshot before subsequent deltas. Joining again is idempotent.
      events.send(connection, "room:snapshot", envelope(id, snapshot));
      if (fresh)
        broadcast(
            room, "participant:joined", Map.of("participant", room.get(connection)), connection);
      if (!observers.containsKey(id)) {
        String watched = id;
        observers.put(id, repository.observe(id, change -> changed(watched, change)));
      }
      return snapshot;
    }
  }

  public synchronized void leave(String connection) {
    String id = subscriptions.remove(connection);
    if (id == null) return;
    var room = rooms.get(id);
    if (room == null) return;
    var participant = room.remove(connection);
    if (participant != null)
      broadcast(room, "participant:left", Map.of("participant", participant), null);
    if (room.empty()) {
      rooms.remove(id);
      var observer = observers.remove(id);
      if (observer != null)
        try {
          observer.close();
        } catch (Exception ignored) {
        }
    }
  }

  private synchronized LiveRoom require(String connection, String uid, String id) {
    var room = rooms.get(id);
    var p = room == null ? null : room.get(connection);
    if (p == null || !p.uid().equals(uid))
      throw new CollaborationProblem("FORBIDDEN", "Debes entrar a la sala");
    return room;
  }

  public Message send(
      String connection, String uid, String rawId, String clientId, String rawText) {
    String id = Message.roomId(rawId);
    require(connection, uid, id);
    var message = repository.persist(uid, id, Message.clientId(clientId), Message.text(rawText));
    synchronized (this) {
      var room = rooms.get(id);
      if (room != null) broadcast(room, "chat:message", Map.of("message", message), null);
    }
    return message;
  }

  public void media(
      String connection, String uid, String rawId, boolean mic, boolean cam, boolean screen) {
    String id = Message.roomId(rawId);
    repository.access(uid, id);
    synchronized (this) {
      var room = require(connection, uid, id);
      room.media(connection, mic, cam, screen);
      broadcast(room, "media:update", Map.of("participant", room.get(connection)), null);
    }
  }

  public void signal(
      String connection, String uid, String rawId, String to, Map<String, Object> signal) {
    String id = Message.roomId(rawId);
    repository.access(uid, id);
    if (signal == null || signal.isEmpty() || signal.toString().length() > 65536)
      throw new CollaborationProblem("INVALID_SIGNAL", "Señal inválida");
    String targetUid;
    synchronized (this) {
      var room = require(connection, uid, id);
      var target = room.get(to);
      if (target == null || connection.equals(to))
        throw new CollaborationProblem("FORBIDDEN", "Destino fuera de la sala");
      targetUid = target.uid();
    }
    repository.access(targetUid, id);
    synchronized (this) {
      var room = require(connection, uid, id);
      if (room.get(to) == null) throw new CollaborationProblem("FORBIDDEN", "Destino desconectado");
      events.send(
          to,
          "rtc:signal",
          envelope(
              id, Map.of("fromConnectionId", connection, "toConnectionId", to, "signal", signal)));
    }
  }

  public CollaborationRepository.Page history(
      String uid, String id, int limit, String before, String after) {
    if (limit < 1 || limit > 100 || (before != null && after != null))
      throw new CollaborationProblem("INVALID_CURSOR", "Paginación inválida");
    return repository.history(uid, Message.roomId(id), limit, before, after);
  }

  private synchronized void changed(String id, CollaborationRepository.RoomChange change) {
    var room = rooms.get(id);
    if (room == null) return;
    if (!change.available()) {
      broadcast(room, "room:unavailable", Map.of("code", "DEPENDENCY_UNAVAILABLE"), null);
      return;
    }
    if (!change.active()) {
      broadcast(room, "room:deleted", Map.of("roomId", id), null);
      for (var p : room.snapshot()) subscriptions.remove(p.connectionId());
      rooms.remove(id);
      var observer = observers.remove(id);
      if (observer != null)
        try {
          observer.close();
        } catch (Exception ignored) {
        }
    } else if (!room.name.equals(change.name())) {
      room.name = change.name();
      broadcast(room, "room:updated", Map.of("name", room.name), null);
    }
  }

  private Map<String, Object> envelope(String id, Object payload) {
    return Map.of(
        "v", 1, "eventId", UUID.randomUUID().toString(), "roomId", id, "payload", payload);
  }

  private void broadcast(LiveRoom room, String event, Object payload, String except) {
    var body = envelope(room.id, payload);
    for (var p : room.snapshot())
      if (!p.connectionId().equals(except)) events.send(p.connectionId(), event, body);
  }

  public void validate(String connection, String uid) {
    String id;
    synchronized (this) {
      id = subscriptions.get(connection);
    }
    if (id != null) repository.access(uid, id);
  }

  public synchronized void close() {
    for (var observer : observers.values())
      try {
        observer.close();
      } catch (Exception ignored) {
      }
    observers.clear();
    rooms.clear();
    subscriptions.clear();
  }
}

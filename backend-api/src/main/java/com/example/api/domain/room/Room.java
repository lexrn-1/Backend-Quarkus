package com.example.api.domain.room;

import static com.example.api.domain.room.RoomProblem.Code.*;

import java.time.Instant;
import java.util.UUID;

public record Room(
    String id, String name, String ownerUid, String status, Instant createdAt, Instant updatedAt) {
  public static String name(String raw) {
    if (raw == null
        || raw.strip().isEmpty()
        || raw.strip().length() > 100
        || raw.codePoints().anyMatch(Character::isISOControl))
      throw new RoomProblem(
          INVALID_ROOM, "Nombre obligatorio de 1 a 100 caracteres, sin caracteres de control");
    return raw.strip();
  }

  public static String id(String value) {
    try {
      String s = value.strip().toLowerCase(java.util.Locale.ROOT);
      if (!UUID.fromString(s).toString().equals(s)
          || UUID.fromString(s).version() != 4
          || UUID.fromString(s).variant() != 2) throw new IllegalArgumentException();
      return s;
    } catch (Exception e) {
      throw new RoomProblem(INVALID_ROOM, "ID de sala inválido");
    }
  }

  public void active() {
    if (!"ACTIVE".equals(status))
      throw new RoomProblem(ROOM_CLOSED, "La sala ya no está disponible");
  }

  public void owner(String uid) {
    if (!ownerUid.equals(uid))
      throw new RoomProblem(FORBIDDEN, "Solo el anfitrión puede modificar la sala");
  }

  public Room rename(String uid, String value) {
    owner(uid);
    active();
    return new Room(id, name(value), ownerUid, status, createdAt, Instant.now());
  }

  public Room deleting(String uid) {
    owner(uid);
    return new Room(id, name, ownerUid, "DELETING", createdAt, Instant.now());
  }
}

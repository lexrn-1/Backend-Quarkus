package com.example.api.application.usecase;

import com.example.api.application.port.in.Rooms;
import com.example.api.application.port.out.RoomRepository;
import com.example.api.domain.room.*;
import java.time.Instant;
import java.util.UUID;

public class RoomsUseCase implements Rooms {
  private final RoomRepository rooms;

  public RoomsUseCase(RoomRepository rooms) {
    this.rooms = rooms;
  }

  public Room create(String uid, String name) {
    var now = Instant.now();
    return rooms.create(
        new Room(UUID.randomUUID().toString(), Room.name(name), uid, "ACTIVE", now, now));
  }

  public Room get(String uid, String id) {
    var room = rooms.find(Room.id(id));
    room.active();
    if (!room.ownerUid().equals(uid) && !rooms.member(room.id(), uid))
      throw new RoomProblem(RoomProblem.Code.FORBIDDEN, "Primero debes unirte a la sala");
    return room;
  }

  public Room join(String uid, String id) {
    return rooms.join(Room.id(id), uid);
  }

  public Room rename(String uid, String id, String name) {
    String clean = Room.name(name);
    return rooms.update(Room.id(id), room -> room.rename(uid, clean), false);
  }

  public Room remove(String uid, String id) {
    return rooms.update(Room.id(id), room -> room.deleting(uid), true);
  }

  public RoomRepository.Page list(String uid, String scope, int limit, String cursor) {
    if (!"owned".equals(scope) && !"member".equals(scope))
      throw new RoomProblem(RoomProblem.Code.INVALID_ROOM, "Scope inválido");
    if (limit < 1 || limit > 50)
      throw new RoomProblem(RoomProblem.Code.INVALID_ROOM, "Límite entre 1 y 50");
    return rooms.list(uid, "member".equals(scope), limit, cursor);
  }
}

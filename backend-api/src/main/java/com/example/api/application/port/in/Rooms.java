package com.example.api.application.port.in;

import com.example.api.application.port.out.RoomRepository;
import com.example.api.domain.room.Room;

public interface Rooms {
  Room create(String uid, String name);

  Room get(String uid, String id);

  Room join(String uid, String id);

  Room rename(String uid, String id, String name);

  Room remove(String uid, String id);

  RoomRepository.Page list(String uid, String scope, int limit, String cursor);
}

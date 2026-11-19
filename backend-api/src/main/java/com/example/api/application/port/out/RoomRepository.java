package com.example.api.application.port.out;

import com.example.api.domain.room.Room;
import java.util.*;
import java.util.function.UnaryOperator;

public interface RoomRepository {
  record Page(List<Room> items, String nextCursor) {}

  Room create(Room room);

  Room find(String id);

  boolean member(String roomId, String uid);

  Room join(String id, String uid);

  Room update(String id, UnaryOperator<Room> action, boolean deletion);

  Page list(String uid, boolean memberScope, int limit, String cursor);
}

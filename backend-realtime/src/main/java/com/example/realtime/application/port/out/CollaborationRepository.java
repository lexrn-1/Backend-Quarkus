package com.example.realtime.application.port.out;

import com.example.realtime.domain.collaboration.*;
import java.util.*;
import java.util.function.Consumer;

public interface CollaborationRepository {
  record Access(String roomId, String roomName, String ownerUid, Message.Sender self) {}

  record RoomChange(String name, boolean active, boolean available) {}

  record Page(List<Message> items, String nextCursor) {}

  Access access(String uid, String roomId);

  Message persist(String uid, String roomId, String clientId, String text);

  Page history(String uid, String roomId, int limit, String before, String after);

  AutoCloseable observe(String roomId, Consumer<RoomChange> listener);
}

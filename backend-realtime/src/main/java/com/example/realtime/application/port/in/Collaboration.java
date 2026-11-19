package com.example.realtime.application.port.in;

import com.example.realtime.application.port.out.CollaborationRepository;
import com.example.realtime.domain.collaboration.Message;
import java.util.Map;

public interface Collaboration {
  Map<String, Object> join(String connectionId, String uid, String roomId);

  void leave(String connectionId);

  Message send(String connectionId, String uid, String roomId, String clientMessageId, String text);

  void media(
      String connectionId, String uid, String roomId, boolean mic, boolean cam, boolean screen);

  void signal(
      String connectionId,
      String uid,
      String roomId,
      String toConnectionId,
      Map<String, Object> signal);

  CollaborationRepository.Page history(
      String uid, String roomId, int limit, String before, String after);

  void validate(String connectionId, String uid);

  void close();
}

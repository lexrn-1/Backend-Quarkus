package com.example.realtime.application.port.out;

public interface ConnectionEvents {
  void send(String connectionId, String event, Object body);
}

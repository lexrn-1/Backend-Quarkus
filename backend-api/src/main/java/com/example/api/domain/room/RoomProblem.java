package com.example.api.domain.room;

public class RoomProblem extends RuntimeException {
  public enum Code {
    INVALID_ROOM,
    ROOM_NOT_FOUND,
    FORBIDDEN,
    ROOM_CLOSED,
    INVALID_CURSOR
  }

  private final Code code;

  public RoomProblem(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}

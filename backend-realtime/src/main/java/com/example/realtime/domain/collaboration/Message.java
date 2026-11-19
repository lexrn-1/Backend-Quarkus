package com.example.realtime.domain.collaboration;

public record Message(
    String id,
    String roomId,
    String clientMessageId,
    Sender sender,
    String text,
    String createdAt,
    String cursor) {
  public record Sender(String uid, String displayName, String avatar) {}

  public static String text(String value) {
    if (value == null || value.strip().isEmpty() || value.strip().length() > 2000)
      throw new CollaborationProblem(
          "INVALID_MESSAGE", "Escribe un mensaje de 1 a 2000 caracteres");
    return value.strip();
  }

  public static String roomId(String value) {
    try {
      String id = value.strip().toLowerCase(java.util.Locale.ROOT);
      if (!java.util.UUID.fromString(id).toString().equals(id)
          || java.util.UUID.fromString(id).version() != 4
          || java.util.UUID.fromString(id).variant() != 2) throw new IllegalArgumentException();
      return id;
    } catch (Exception e) {
      throw new CollaborationProblem("INVALID_ROOM", "ID de sala inválido");
    }
  }

  public static String clientId(String value) {
    try {
      if (!java.util.UUID.fromString(value).toString().equals(value)
          || java.util.UUID.fromString(value).version() != 4
          || java.util.UUID.fromString(value).variant() != 2) throw new IllegalArgumentException();
      return value;
    } catch (Exception e) {
      throw new CollaborationProblem("INVALID_MESSAGE", "clientMessageId debe ser UUID");
    }
  }
}

package com.example.realtime.domain.collaboration;

public record Participant(
    String connectionId,
    String uid,
    String displayName,
    String avatar,
    boolean micOn,
    boolean camOn,
    boolean screenSharing) {
  public Participant media(boolean mic, boolean cam, boolean screen) {
    return new Participant(connectionId, uid, displayName, avatar, mic, cam, screen);
  }
}

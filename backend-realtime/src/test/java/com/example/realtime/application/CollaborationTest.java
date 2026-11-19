package com.example.realtime.application;

import static org.junit.jupiter.api.Assertions.*;

import com.example.realtime.application.port.out.*;
import com.example.realtime.application.usecase.CollaborationUseCases;
import com.example.realtime.domain.collaboration.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CollaborationTest {
  private static class Repository implements CollaborationRepository {
    final Map<String, Message> messages = new ConcurrentHashMap<>();
    final Map<String, Consumer<RoomChange>> listeners = new HashMap<>();

    public Access access(String uid, String room) {
      if (uid.equals("outsider")) throw new CollaborationProblem("FORBIDDEN", "Denied");
      return new Access(room, "Study", "owner", new Message.Sender(uid, uid, "avatar-1"));
    }

    public Message persist(String uid, String room, String id, String text) {
      access(uid, room);
      return messages.computeIfAbsent(
          room + uid + id,
          key ->
              new Message(
                  key,
                  room,
                  id,
                  new Message.Sender(uid, uid, "avatar-1"),
                  text,
                  "2026-11-19T00:00:00.000000000Z",
                  "cursor"));
    }

    public Page history(String uid, String room, int limit, String before, String after) {
      access(uid, room);
      return new Page(List.copyOf(messages.values()), null);
    }

    public AutoCloseable observe(String id, Consumer<RoomChange> callback) {
      listeners.put(id, callback);
      return () -> listeners.remove(id);
    }
  }

  private record Event(String to, String type, Object body) {}

  @Test
  void sixConnectionsAdmittedAtomically() throws Exception {
    var repository = new Repository();
    var app = new CollaborationUseCases(repository, (c, e, b) -> {});
    String room = UUID.randomUUID().toString();
    var pool = Executors.newFixedThreadPool(7);
    var start = new CountDownLatch(1);
    var futures = new ArrayList<Future<Boolean>>();
    for (int i = 0; i < 7; i++) {
      int n = i;
      futures.add(
          pool.submit(
              () -> {
                start.await();
                try {
                  app.join("c" + n, "u" + n, room);
                  return true;
                } catch (CollaborationProblem p) {
                  assertEquals("ROOM_FULL", p.code());
                  return false;
                }
              }));
    }
    start.countDown();
    int admitted = 0;
    for (var f : futures) if (f.get()) admitted++;
    pool.shutdownNow();
    assertEquals(6, admitted);
    app.close();
  }

  @Test
  void idempotentMessageAndCrossRoomIsolation() {
    var repo = new Repository();
    var events = new ArrayList<Event>();
    var app = new CollaborationUseCases(repo, (c, e, b) -> events.add(new Event(c, e, b)));
    String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    app.join("a", "owner", a);
    app.join("b", "guest", b);
    String id = UUID.randomUUID().toString();
    var first = app.send("a", "owner", a, id, " hi ");
    var again = app.send("a", "owner", a, id, "hi");
    assertEquals(first.id(), again.id());
    assertEquals(1, repo.messages.size());
    assertTrue(
        events.stream()
            .filter(e -> e.type().equals("chat:message"))
            .allMatch(e -> e.to().equals("a")));
    assertThrows(
        CollaborationProblem.class,
        () -> app.signal("a", "owner", a, "b", Map.of("type", "offer")));
    assertThrows(
        CollaborationProblem.class,
        () -> app.send("b", "guest", a, UUID.randomUUID().toString(), "intrusion"));
  }

  @Test
  void snapshotsAndDeletionInvalidatePresence() {
    var repo = new Repository();
    var events = new ArrayList<Event>();
    var app = new CollaborationUseCases(repo, (c, e, b) -> events.add(new Event(c, e, b)));
    String id = UUID.randomUUID().toString();
    app.join("a", "owner", id);
    app.media("a", "owner", id, true, false, true);
    var snapshot = app.join("b", "guest", id);
    var participants = (List<Participant>) snapshot.get("participants");
    assertTrue(participants.getFirst().screenSharing());
    assertFalse(participants.getFirst().camOn());
    repo.listeners.get(id).accept(new CollaborationRepository.RoomChange("", false, false));
    assertTrue(events.stream().noneMatch(e -> e.type().equals("room:deleted")));
    repo.listeners.get(id).accept(new CollaborationRepository.RoomChange("", false, true));
    assertEquals(2, events.stream().filter(e -> e.type().equals("room:deleted")).count());
    assertThrows(
        CollaborationProblem.class,
        () -> app.send("a", "owner", id, UUID.randomUUID().toString(), "closed"));
  }

  @Test
  void textAndClientIdsAreValidated() {
    for (String text : new String[] {"", "   ", "x".repeat(2001)})
      assertThrows(CollaborationProblem.class, () -> Message.text(text));
    assertThrows(CollaborationProblem.class, () -> Message.clientId("../spoof"));
  }
}

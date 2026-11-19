package com.example.api.domain.room;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoomTest {
  private Room room() {
    return new Room(
        UUID.randomUUID().toString(), "Study", "owner", "ACTIVE", Instant.now(), Instant.now());
  }

  @Test
  void ownerOnly() {
    var r = room();
    assertThrows(RoomProblem.class, () -> r.rename("guest", "New"));
    assertThrows(RoomProblem.class, () -> r.deleting("guest"));
    assertEquals("New", r.rename("owner", " New ").name());
  }

  @Test
  void validation() {
    assertEquals("Study", Room.name(" Study "));
    for (String name : new String[] {"", "   ", "x".repeat(101), "A\nB"})
      assertThrows(RoomProblem.class, () -> Room.name(name));
    assertThrows(RoomProblem.class, () -> Room.id("SR-1234"));
    var id = UUID.randomUUID().toString();
    assertEquals(id, Room.id(" " + id.toUpperCase() + " "));
  }

  @Test
  void closingIsIdempotentAndRejectsNewChanges() {
    var closing = room().deleting("owner");
    assertEquals("DELETING", closing.deleting("owner").status());
    assertThrows(RoomProblem.class, closing::active);
    assertThrows(RoomProblem.class, () -> closing.rename("owner", "Other"));
  }
}

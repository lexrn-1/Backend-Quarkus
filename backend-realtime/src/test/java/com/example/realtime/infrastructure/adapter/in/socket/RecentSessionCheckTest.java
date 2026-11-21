package com.example.realtime.infrastructure.adapter.in.socket;

import static org.junit.jupiter.api.Assertions.*;
import com.example.realtime.application.port.out.TokenVerifier.Identity;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RecentSessionCheckTest {
  @Test void burstChecksAreBoundedAndChatAlwaysRechecks() {
    var check = new RecentSessionCheck();
    var identity = new Identity("u", 10000, "hash", 0);
    var calls = new AtomicInteger();
    java.util.function.BooleanSupplier verify = () -> { calls.incrementAndGet(); return true; };
    assertTrue(check.active(identity, 1000, true, verify));
    assertTrue(check.active(identity, 2999, true, verify));
    assertEquals(1, calls.get());
    assertTrue(check.active(identity, 3000, true, verify));
    assertEquals(2, calls.get());
    assertTrue(check.active(identity, 3001, false, verify));
    assertEquals(3, calls.get());
    assertFalse(check.active(identity, 10000, true, verify));
  }

  @Test void failedCheckAndDifferentTokenNeverReuseSuccess() {
    var check = new RecentSessionCheck();
    var old = new Identity("u", 10000, "old", 0);
    var fresh = new Identity("u", 10000, "new", 0);
    assertTrue(check.active(old, 1000, true, () -> true));
    assertFalse(check.active(fresh, 1001, true, () -> false));
    assertFalse(check.active(old, 1002, true, () -> false));
    assertFalse(check.active(null, 1003, true, () -> true));
  }
}

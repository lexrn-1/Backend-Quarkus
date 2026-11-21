package com.example.realtime.infrastructure.adapter.in.socket;

import com.example.realtime.application.port.out.TokenVerifier.Identity;
import java.util.function.BooleanSupplier;

/** Per-connection successful check, only reused for bursts of media/ICE events. */
final class RecentSessionCheck {
  private Identity identity;
  private long checkedAt;

  synchronized boolean active(Identity candidate, long now, boolean burst, BooleanSupplier verify) {
    if (candidate == null || candidate.expiresAt() <= now) {
      identity = null;
      return false;
    }
    if (burst && candidate.equals(identity) && now >= checkedAt && now - checkedAt < 2000)
      return true;
    identity = null;
    if (!verify.getAsBoolean()) return false;
    identity = candidate;
    checkedAt = now;
    return true;
  }
}

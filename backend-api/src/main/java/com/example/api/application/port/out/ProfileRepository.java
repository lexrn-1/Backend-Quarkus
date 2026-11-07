package com.example.api.application.port.out;
import com.example.api.domain.entity.*;
import java.util.Optional;
public interface ProfileRepository {
    Optional<UserProfile> find(String uid);
    /** Atomically reserves normalized username and completes the profile; identical retries are safe. */
    UserProfile complete(AuthenticatedUser user, ProfileDraft draft);
}

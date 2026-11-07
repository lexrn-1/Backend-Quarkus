package com.example.api.application.usecase;
import com.example.api.application.port.in.ReadSession;
import com.example.api.application.port.out.ProfileRepository;
import com.example.api.domain.entity.*;
public class ReadSessionUseCase implements ReadSession {
    private final ProfileRepository profiles;
    public ReadSessionUseCase(ProfileRepository profiles) { this.profiles = profiles; }
    public UserProfile execute(AuthenticatedUser user) {
        // A Firebase account without a Firestore profile is a recoverable pending profile.
        // GET remains read-only; the first successful completion creates the document atomically.
        var profile = profiles.find(user.uid()).orElseGet(() -> new UserProfile(user.uid(), user.email(),
            null, null, null, null, null, UserProfile.Status.PENDING, null, null));
        profile.requireAvailable(); return profile;
    }
}

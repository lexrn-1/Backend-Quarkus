package com.example.api.application.usecase;
import com.example.api.application.port.in.ReadSession;
import com.example.api.application.port.out.ProfileRepository;
import com.example.api.domain.entity.*;
public class ReadSessionUseCase implements ReadSession {
    private final ProfileRepository profiles;
    public ReadSessionUseCase(ProfileRepository profiles) { this.profiles = profiles; }
    public UserProfile execute(AuthenticatedUser user) {
        var profile = profiles.ensurePending(user); profile.requireAvailable(); return profile;
    }
}

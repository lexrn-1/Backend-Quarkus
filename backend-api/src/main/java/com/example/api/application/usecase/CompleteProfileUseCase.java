package com.example.api.application.usecase;
import com.example.api.application.port.in.CompleteProfile;
import com.example.api.application.port.out.ProfileRepository;
import com.example.api.domain.entity.*;
public class CompleteProfileUseCase implements CompleteProfile {
    private final ProfileRepository profiles;
    private final EmailPolicy emails;
    public CompleteProfileUseCase(ProfileRepository profiles, EmailPolicy emails) { this.profiles = profiles; this.emails = emails; }
    public UserProfile execute(AuthenticatedUser user, ProfileDraft draft) {
        emails.validate(user.email());
        return profiles.complete(user, draft);
    }
}

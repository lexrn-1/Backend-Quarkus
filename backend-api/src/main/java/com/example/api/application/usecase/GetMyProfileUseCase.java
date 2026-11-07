package com.example.api.application.usecase;
import com.example.api.application.port.in.GetMyProfile;
import com.example.api.application.port.out.ProfileRepository;
import com.example.api.domain.entity.*;
import com.example.api.domain.exception.IdentityException;
public class GetMyProfileUseCase implements GetMyProfile {
    private final ProfileRepository profiles;
    public GetMyProfileUseCase(ProfileRepository profiles) { this.profiles = profiles; }
    public UserProfile execute(AuthenticatedUser user) {
        var profile = profiles.find(user.uid()).orElseThrow(() -> new IdentityException(
            IdentityException.Kind.PROFILE_INCOMPLETE, "Completa tu perfil para continuar"));
        profile.requireAvailable();
        if (!profile.complete()) throw new IdentityException(IdentityException.Kind.PROFILE_INCOMPLETE, "Completa tu perfil para continuar");
        return profile;
    }
}

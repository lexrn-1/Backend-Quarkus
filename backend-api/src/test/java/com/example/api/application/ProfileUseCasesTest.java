package com.example.api.application;

import com.example.api.application.port.out.*;
import com.example.api.application.usecase.*;
import com.example.api.domain.entity.*;
import com.example.api.domain.exception.IdentityException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileUseCasesTest {
    private final AuthenticatedUser identity = new AuthenticatedUser("trusted-uid","ana@example.org",null,null,"password","hash",Instant.now().plusSeconds(60));
    private final ProfileDraft draft = new ProfileDraft("Ana","Pérez","student","avatar-1");
    private static class Profiles implements ProfileRepository {
        UserProfile stored;
        public Optional<UserProfile> find(String uid) { return Optional.ofNullable(stored); }
        public UserProfile ensurePending(AuthenticatedUser u) {
            if (stored == null) stored = new UserProfile(u.uid(),u.email(),null,null,null,null,null,UserProfile.Status.PENDING,null,null);
            return stored;
        }
        public UserProfile complete(AuthenticatedUser u, ProfileDraft d) {
            stored = new UserProfile(u.uid(),u.email(),d.firstName(),d.lastName(),d.username(),d.usernameNormalized(),d.avatar(),UserProfile.Status.COMPLETE,null,null);
            return stored;
        }
    }
    @Test void pendingIdentityCannotReadProtectedProfile() {
        var repository = new Profiles();
        assertEquals(UserProfile.Status.PENDING,new ReadSessionUseCase(repository).execute(identity).status());
        assertThrows(IdentityException.class,()->new GetMyProfileUseCase(repository).execute(identity));
    }
    @Test void completeUsesIdentityUidAndEmail() {
        var repository = new Profiles();
        var profile = new CompleteProfileUseCase(repository,new EmailPolicy(Set.of())).execute(identity,draft);
        assertEquals("trusted-uid",profile.uid()); assertEquals(identity.email(),profile.email());
        assertEquals(profile,new GetMyProfileUseCase(repository).execute(identity));
    }
    @Test void domainPolicyRejectsBeforeWriting() {
        var repository = new Profiles();
        assertThrows(IdentityException.class,()->new CompleteProfileUseCase(repository,new EmailPolicy(Set.of("university.edu.co"))).execute(identity,draft));
        assertNull(repository.stored);
    }
    @Test void unavailableAccountIsBlocked() {
        var repository = new Profiles();
        repository.stored = new UserProfile(identity.uid(),identity.email(),null,null,null,null,null,UserProfile.Status.DELETION_PENDING,null,null);
        assertThrows(IdentityException.class,()->new ReadSessionUseCase(repository).execute(identity));
    }
}

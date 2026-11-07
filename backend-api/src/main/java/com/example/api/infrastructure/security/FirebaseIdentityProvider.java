package com.example.api.infrastructure.security;

import com.example.api.application.port.out.*;
import com.example.api.domain.exception.*;
import io.quarkus.security.*;
import io.quarkus.security.identity.*;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FirebaseIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {
    @Inject SessionGateway sessions;
    @Inject ProfileRepository profiles;
    public Class<TokenAuthenticationRequest> getRequestType() { return TokenAuthenticationRequest.class; }
    public Uni<SecurityIdentity> authenticate(TokenAuthenticationRequest request, AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            try {
                var session = sessions.verify(request.getToken().getToken());
                var profile = profiles.find(session.uid());
                String role = profile.isEmpty() ? "pending" : profile.get().complete() ? "user"
                    : profile.get().status() == com.example.api.domain.entity.UserProfile.Status.PENDING ? "pending" : "unavailable";
                return QuarkusSecurityIdentity.builder().setPrincipal(() -> session.uid())
                    .addRole(role).addAttribute("firebaseSession",session).build();
            } catch (IdentityException e) { throw new AuthenticationFailedException(); }
            catch (DependencyUnavailableException e) { throw new AuthenticationFailedException(e); }
        });
    }
}

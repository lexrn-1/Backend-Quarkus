package com.example.api.infrastructure.security;
import com.example.api.domain.entity.AuthenticatedUser;
import com.example.api.domain.exception.IdentityException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
@RequestScoped
public class CurrentFirebaseSession {
    @Inject SecurityIdentity identity;
    public AuthenticatedUser get() {
        AuthenticatedUser user = identity.getAttribute("firebaseSession");
        if (user == null) throw IdentityException.unauthorized();
        return user;
    }
}

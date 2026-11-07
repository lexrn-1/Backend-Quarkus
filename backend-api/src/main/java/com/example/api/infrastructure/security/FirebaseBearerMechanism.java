package com.example.api.infrastructure.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.*;
import io.quarkus.security.identity.request.*;
import io.quarkus.vertx.http.runtime.security.*;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

@ApplicationScoped
public class FirebaseBearerMechanism implements HttpAuthenticationMechanism {
    public Uni<SecurityIdentity> authenticate(RoutingContext ctx, IdentityProviderManager manager) {
        String auth = ctx.request().getHeader("Authorization");
        if (auth == null) return Uni.createFrom().nullItem();
        if (!auth.regionMatches(true,0,"Bearer ",0,7) || auth.substring(7).isBlank())
            return Uni.createFrom().failure(new AuthenticationFailedException());
        return manager.authenticate(new TokenAuthenticationRequest(new TokenCredential(auth.substring(7).strip(),"bearer")))
            .onFailure().invoke(error -> {
                if (error.getCause() instanceof com.example.api.domain.exception.DependencyUnavailableException)
                    ctx.put("firebaseUnavailable",true);
            });
    }
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() { return Set.of(TokenAuthenticationRequest.class); }
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext ctx) {
        return Uni.createFrom().item(new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION,"bearer"));
    }
    public Uni<ChallengeData> getChallenge(RoutingContext ctx) {
        return Uni.createFrom().item(Boolean.TRUE.equals(ctx.get("firebaseUnavailable"))
            ? new ChallengeData(503,null,null) : new ChallengeData(401,"WWW-Authenticate","Bearer"));
    }
    public Uni<Boolean> sendChallenge(RoutingContext ctx) {
        boolean unavailable = Boolean.TRUE.equals(ctx.get("firebaseUnavailable"));
        if (!ctx.response().ended()) {
            ctx.response().setStatusCode(unavailable ? 503 : 401).putHeader("Content-Type","application/json")
                .putHeader("Cache-Control","no-store");
            if (!unavailable) ctx.response().putHeader("WWW-Authenticate","Bearer");
            ctx.response().end(unavailable
                ? "{\"code\":\"DEPENDENCY_UNAVAILABLE\",\"message\":\"Dependencia no disponible\"}"
                : "{\"code\":\"UNAUTHORIZED\",\"message\":\"Sesión inválida o expirada\"}");
        }
        return Uni.createFrom().item(true);
    }
}

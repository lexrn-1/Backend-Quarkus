package com.example.api.infrastructure.adapter.in.rest.exception;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import java.util.Map;
@Provider
public class ForbiddenMapper implements ExceptionMapper<ForbiddenException> {
    @Inject SecurityIdentity identity;
    public Response toResponse(ForbiddenException e) {
        boolean pending = identity.hasRole("pending");
        return Response.status(403).type(MediaType.APPLICATION_JSON_TYPE).header("Cache-Control","no-store")
            .entity(Map.of("code",pending?"PROFILE_INCOMPLETE":"FORBIDDEN",
                "message",pending?"Completa tu perfil para continuar":"Acceso no permitido")).build();
    }
}

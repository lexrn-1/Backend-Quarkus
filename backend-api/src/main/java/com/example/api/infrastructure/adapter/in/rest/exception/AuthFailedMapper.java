package com.example.api.infrastructure.adapter.in.rest.exception;
import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import java.util.Map;
@Provider
public class AuthFailedMapper implements ExceptionMapper<AuthenticationFailedException> {
    public Response toResponse(AuthenticationFailedException e) {
        boolean unavailable = e.getCause() instanceof com.example.api.domain.exception.DependencyUnavailableException;
        var result = Response.status(unavailable?503:401).type(MediaType.APPLICATION_JSON_TYPE).header("Cache-Control","no-store")
            .entity(Map.of("code",unavailable?"DEPENDENCY_UNAVAILABLE":"UNAUTHORIZED",
                "message",unavailable?"Dependencia no disponible":"Sesión inválida o expirada"));
        if (!unavailable) result.header("WWW-Authenticate","Bearer");
        return result.build();
    }
}

package com.example.api.infrastructure.adapter.in.rest.exception;
import com.example.api.domain.exception.IdentityException;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import java.util.Map;
@Provider
public class IdentityExceptionMapper implements ExceptionMapper<IdentityException> {
    public Response toResponse(IdentityException e) {
        int status = switch(e.kind()) {
            case INVALID_PROFILE -> 400;
            case UNAUTHORIZED -> 401;
            case USERNAME_TAKEN, PROFILE_ALREADY_COMPLETE -> 409;
            default -> 403;
        };
        var response = Response.status(status).type(MediaType.APPLICATION_JSON_TYPE).header("Cache-Control","no-store")
            .entity(Map.of("code",e.kind().name(),"message",e.getMessage()));
        if (status == 401) response.header("WWW-Authenticate","Bearer");
        return response.build();
    }
}

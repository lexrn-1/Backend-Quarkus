package com.example.api.infrastructure.adapter.in.rest.exception;
import com.example.api.domain.exception.DependencyUnavailableException;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import java.util.Map;
@Provider
public class UnavailableMapper implements ExceptionMapper<DependencyUnavailableException> {
    public Response toResponse(DependencyUnavailableException e) {
        return Response.status(503).type(MediaType.APPLICATION_JSON_TYPE)
            .entity(Map.of("code","DEPENDENCY_UNAVAILABLE","message",e.getMessage())).build();
    }
}

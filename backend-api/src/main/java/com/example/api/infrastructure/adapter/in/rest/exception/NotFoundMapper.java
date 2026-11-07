package com.example.api.infrastructure.adapter.in.rest.exception;
import com.example.api.domain.exception.DemoNotFoundException;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import java.util.Map;
@Provider
public class NotFoundMapper implements ExceptionMapper<DemoNotFoundException> {
    public Response toResponse(DemoNotFoundException e) {
        return Response.status(404).type(MediaType.APPLICATION_JSON_TYPE)
            .entity(Map.of("code","DEMO_NOT_FOUND","message",e.getMessage())).build();
    }
}

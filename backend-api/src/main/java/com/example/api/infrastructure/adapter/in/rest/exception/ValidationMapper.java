package com.example.api.infrastructure.adapter.in.rest.exception;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.ext.*;
import jakarta.ws.rs.core.*;
import java.util.Map;
@Provider
public class ValidationMapper implements ExceptionMapper<ConstraintViolationException> {
    public Response toResponse(ConstraintViolationException e) {
        return Response.status(400).type(MediaType.APPLICATION_JSON_TYPE).entity(Map.of("code","INVALID_PROFILE",
            "message","Revisa los campos obligatorios y sus longitudes",
            "fields",e.getConstraintViolations().stream().map(v -> Map.of("field",v.getPropertyPath().toString(),"message",v.getMessage())).toList())).build();
    }
}

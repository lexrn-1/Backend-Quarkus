package com.example.realtime.infrastructure.adapter.in.rest;

import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.Map;

@Provider
public class HistoryAuthErrors implements ExceptionMapper<SecurityException> {
  public Response toResponse(SecurityException e) {
    return Response.status(401)
        .header("WWW-Authenticate", "Bearer")
        .header("Cache-Control", "no-store")
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(Map.of("code", "UNAUTHORIZED", "message", "Sesión inválida o perfil incompleto"))
        .build();
  }
}

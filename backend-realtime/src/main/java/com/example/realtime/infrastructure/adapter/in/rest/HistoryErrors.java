package com.example.realtime.infrastructure.adapter.in.rest;

import com.example.realtime.domain.collaboration.CollaborationProblem;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.Map;

@Provider
public class HistoryErrors implements ExceptionMapper<CollaborationProblem> {
  public Response toResponse(CollaborationProblem p) {
    int status =
        switch (p.code()) {
          case "FORBIDDEN" -> 403;
          case "ROOM_NOT_FOUND" -> 404;
          case "ROOM_CLOSED", "MESSAGE_ID_CONFLICT" -> 409;
          case "DEPENDENCY_UNAVAILABLE" -> 503;
          default -> 400;
        };
    return Response.status(status)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .header("Cache-Control", "no-store")
        .entity(Map.of("code", p.code(), "message", p.getMessage()))
        .build();
  }
}

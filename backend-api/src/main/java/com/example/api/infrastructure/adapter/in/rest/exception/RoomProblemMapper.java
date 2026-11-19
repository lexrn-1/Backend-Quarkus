package com.example.api.infrastructure.adapter.in.rest.exception;

import com.example.api.domain.room.RoomProblem;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import java.util.Map;

@Provider
public class RoomProblemMapper implements ExceptionMapper<RoomProblem> {
  public Response toResponse(RoomProblem p) {
    int status =
        switch (p.code()) {
          case FORBIDDEN -> 403;
          case ROOM_NOT_FOUND -> 404;
          case ROOM_CLOSED -> 409;
          default -> 400;
        };
    return Response.status(status)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(Map.of("code", p.code().name(), "message", p.getMessage()))
        .build();
  }
}

package com.example.api.infrastructure.adapter.in.rest.controller;

import com.example.api.application.port.in.Rooms;
import com.example.api.domain.room.Room;
import com.example.api.infrastructure.security.CurrentFirebaseSession;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Instant;
import java.util.*;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/v1/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@SecurityRequirement(name = "FirebaseBearer")
public class RoomsController {
  @Inject Rooms rooms;
  @Inject CurrentFirebaseSession current;

  public record Name(String name) {}

  public record View(
      String id,
      String name,
      String ownerUid,
      String role,
      String status,
      Instant createdAt,
      Instant updatedAt) {}

  public record Page(List<View> items, String nextCursor) {}

  private View view(Room room) {
    return new View(
        room.id(),
        room.name(),
        room.ownerUid(),
        room.ownerUid().equals(current.get().uid()) ? "OWNER" : "MEMBER",
        room.status(),
        room.createdAt(),
        room.updatedAt());
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public Response create(@Valid Name input) {
    return Response.status(201)
        .entity(view(rooms.create(current.get().uid(), input == null ? null : input.name())))
        .build();
  }

  @GET
  public Page list(
      @QueryParam("scope") @DefaultValue("owned") String scope,
      @QueryParam("limit") @DefaultValue("20") int limit,
      @QueryParam("cursor") String cursor) {
    var page = rooms.list(current.get().uid(), scope, limit, cursor);
    return new Page(page.items().stream().map(this::view).toList(), page.nextCursor());
  }

  @GET
  @Path("/{id}")
  public View get(@PathParam("id") String id) {
    return view(rooms.get(current.get().uid(), id));
  }

  @POST
  @Path("/{id}/join")
  public View join(@PathParam("id") String id) {
    return view(rooms.join(current.get().uid(), id));
  }

  @PATCH
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  public View rename(@PathParam("id") String id, @Valid Name input) {
    return view(rooms.rename(current.get().uid(), id, input == null ? null : input.name()));
  }

  @DELETE
  @Path("/{id}")
  public Response remove(@PathParam("id") String id) {
    return Response.accepted(
            Map.of("roomId", rooms.remove(current.get().uid(), id).id(), "status", "DELETING"))
        .build();
  }
}

package com.example.realtime.infrastructure.adapter.in.rest;

import com.example.realtime.application.port.in.Collaboration;
import com.example.realtime.application.port.out.*;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.*;

@Path("/api/v1/rooms/{id}/messages")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@SecurityScheme(
    securitySchemeName = "FirebaseBearer",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "Firebase ID token")
@SecurityRequirement(name = "FirebaseBearer")
public class HistoryController {
  @Inject Collaboration collaboration;
  @Inject TokenVerifier tokens;

  @GET
  public Response get(
      @HeaderParam("Authorization") String auth,
      @PathParam("id") String id,
      @QueryParam("limit") @DefaultValue("30") int limit,
      @QueryParam("before") String before,
      @QueryParam("after") String after) {
    if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7))
      throw new SecurityException("Sesión requerida");
    var identity = tokens.verifySession(auth.substring(7).strip());
    return Response.ok(collaboration.history(identity.uid(), id, limit, before, after))
        .header("Cache-Control", "no-store")
        .build();
  }
}

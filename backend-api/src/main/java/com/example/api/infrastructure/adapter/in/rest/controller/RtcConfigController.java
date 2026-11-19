package com.example.api.infrastructure.adapter.in.rest.controller;

import com.example.api.application.port.in.IceConfiguration;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/rtc/config")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
@org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement(name = "FirebaseBearer")
public class RtcConfigController {
  @Inject IceConfiguration ice;

  @GET
  public jakarta.ws.rs.core.Response get() {
    return jakarta.ws.rs.core.Response.ok(ice.get()).header("Cache-Control", "no-store").build();
  }
}

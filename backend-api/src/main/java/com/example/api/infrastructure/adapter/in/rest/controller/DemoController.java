package com.example.api.infrastructure.adapter.in.rest.controller;
import com.example.api.application.port.in.ReadDemo;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.Blocking;
import org.eclipse.microprofile.openapi.annotations.Operation;
@Path("/api/v1/demo")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class DemoController {
    @Inject ReadDemo read;
    public record DemoResponse(String message) {}
    @GET @Operation(summary="Lee el dato público de demostración de Firestore")
    @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode="200",description="Mensaje público")
    @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode="404",description="Documento ausente")
    @org.eclipse.microprofile.openapi.annotations.responses.APIResponse(responseCode="503",description="Dependencia no disponible")
    public DemoResponse get() { return new DemoResponse(read.execute().message()); }
}

package com.example.api.infrastructure.adapter.in.rest.controller;

import com.example.api.application.port.in.*;
import com.example.api.domain.entity.*;
import com.example.api.infrastructure.adapter.in.rest.dto.*;
import com.example.api.infrastructure.security.CurrentFirebaseSession;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import io.smallrye.common.annotation.Blocking;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;

@Path("/api/v1") @Produces(MediaType.APPLICATION_JSON) @Blocking
@SecurityRequirement(name="FirebaseBearer")
public class IdentityController {
    @Inject CurrentFirebaseSession current;
    @Inject ReadSession readSession;
    @Inject CompleteProfile complete;
    @Inject GetMyProfile myProfile;
    @Inject Logout logout;
    public record SessionResponse(String uid,String email,String displayName,String photoUrl,String provider,
                                  String status,boolean profileComplete,String nextAction) {}
    @GET @Path("/session") @Operation(summary="Consulta la sesión; sin perfil persistido devuelve PENDING de forma reintentable")
    public SessionResponse session() {
        var identity = current.get(); var profile = readSession.execute(identity);
        return new SessionResponse(identity.uid(),identity.email(),identity.displayName(),identity.photoUrl(),identity.provider(),
            profile.status().name(),profile.complete(),profile.complete()?"ENTER":"COMPLETE_PROFILE");
    }
    @POST @Path("/me/complete") @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary="Completa perfil y reserva username de forma atómica; reintento idéntico idempotente")
    public ProfileResponse complete(@Valid CompleteProfileRequest request) {
        return ProfileResponse.from(complete.execute(current.get(),
            new ProfileDraft(request.firstName(),request.lastName(),request.username(),request.avatar())));
    }
    @GET @Path("/me") @Operation(summary="Perfil propio; requiere perfil completo")
    public ProfileResponse me() { return ProfileResponse.from(myProfile.execute(current.get())); }
    @POST @Path("/session/logout") @Operation(summary="Invalida el ID token actual; el cliente también debe ejecutar Firebase signOut")
    public Response logout() { logout.execute(current.get()); return Response.noContent().header("Cache-Control","no-store").build(); }
}

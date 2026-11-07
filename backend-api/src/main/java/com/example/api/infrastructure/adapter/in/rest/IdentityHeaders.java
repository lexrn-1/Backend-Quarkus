package com.example.api.infrastructure.adapter.in.rest;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.ext.Provider;
@Provider
public class IdentityHeaders implements ContainerResponseFilter {
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (request.getUriInfo().getPath().startsWith("api/v1/")) response.getHeaders().putSingle("Cache-Control","no-store");
    }
}

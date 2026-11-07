package com.example.api.infrastructure.adapter.in.rest;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.ext.Provider;
@Provider
public class IdentityHeaders implements ContainerResponseFilter {
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String path = request.getUriInfo().getPath();
        if (path.startsWith("api/v1/") || path.startsWith("/api/v1/")) response.getHeaders().putSingle("Cache-Control","no-store");
    }
}

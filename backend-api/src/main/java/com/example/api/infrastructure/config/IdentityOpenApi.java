package com.example.api.infrastructure.config;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
@SecurityScheme(securitySchemeName="FirebaseBearer",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="Firebase ID token")
public class IdentityOpenApi {}

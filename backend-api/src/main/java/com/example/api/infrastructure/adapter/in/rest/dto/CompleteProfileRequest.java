package com.example.api.infrastructure.adapter.in.rest.dto;
import jakarta.validation.constraints.*;
public record CompleteProfileRequest(@NotBlank @Size(max=80) String firstName,
        @NotBlank @Size(max=80) String lastName, @NotBlank @Size(max=64) String username,
        @NotBlank @Size(max=2048) String avatar) {}

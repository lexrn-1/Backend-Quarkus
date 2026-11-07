package com.example.api.infrastructure.adapter.in.rest.dto;
import com.example.api.domain.entity.UserProfile;
import java.time.Instant;
public record ProfileResponse(String uid,String email,String firstName,String lastName,String username,
                              String avatar,String status,Instant createdAt,Instant updatedAt) {
    public static ProfileResponse from(UserProfile p) {
        return new ProfileResponse(p.uid(),p.email(),p.firstName(),p.lastName(),p.username(),p.avatar(),p.status().name(),p.createdAt(),p.updatedAt());
    }
}

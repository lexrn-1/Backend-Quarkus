package com.example.api.application.port.out;
import com.example.api.domain.entity.AuthenticatedUser;
public interface SessionGateway {
    AuthenticatedUser verify(String token);
    void logout(AuthenticatedUser user);
}

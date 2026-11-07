package com.example.api.application.usecase;
import com.example.api.application.port.in.Logout;
import com.example.api.application.port.out.SessionGateway;
import com.example.api.domain.entity.AuthenticatedUser;
public class LogoutUseCase implements Logout {
    private final SessionGateway sessions;
    public LogoutUseCase(SessionGateway sessions) { this.sessions = sessions; }
    public void execute(AuthenticatedUser user) { sessions.logout(user); }
}

package com.example.api.infrastructure.config;

import com.example.api.application.port.in.*;
import com.example.api.application.port.out.*;
import com.example.api.application.usecase.*;
import com.example.api.domain.entity.EmailPolicy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import java.util.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class UseCaseConfig {
  @Produces
  @Dependent
  Rooms rooms(RoomRepository repository) {
    return new RoomsUseCase(repository);
  }

  @Produces
  @Dependent
  ReadDemo readDemo(DemoReader reader) {
    return new ReadDemoUseCase(reader);
  }

  @Produces
  @Dependent
  ReadSession readSession(ProfileRepository profiles) {
    return new ReadSessionUseCase(profiles);
  }

  @Produces
  @Dependent
  GetMyProfile getMyProfile(ProfileRepository profiles) {
    return new GetMyProfileUseCase(profiles);
  }

  @Produces
  @Dependent
  CompleteProfile completeProfile(
      ProfileRepository profiles,
      @ConfigProperty(name = "app.identity.allowed-email-domains") Optional<String> domains) {
    return new CompleteProfileUseCase(
        profiles, new EmailPolicy(new HashSet<>(Arrays.asList(domains.orElse("").split(",")))));
  }

  @Produces
  @Dependent
  Logout logout(SessionGateway sessions) {
    return new LogoutUseCase(sessions);
  }
}

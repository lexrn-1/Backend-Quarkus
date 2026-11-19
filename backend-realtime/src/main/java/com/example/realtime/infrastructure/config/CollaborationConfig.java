package com.example.realtime.infrastructure.config;

import com.example.realtime.application.port.in.Collaboration;
import com.example.realtime.application.port.out.*;
import com.example.realtime.application.usecase.CollaborationUseCases;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class CollaborationConfig {
  @Produces
  @ApplicationScoped
  Collaboration collaboration(CollaborationRepository repository, ConnectionEvents events) {
    return new CollaborationUseCases(repository, events);
  }
}

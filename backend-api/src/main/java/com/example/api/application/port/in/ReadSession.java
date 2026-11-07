package com.example.api.application.port.in;
import com.example.api.domain.entity.*;
public interface ReadSession { UserProfile execute(AuthenticatedUser user); }

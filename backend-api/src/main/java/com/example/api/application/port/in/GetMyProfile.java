package com.example.api.application.port.in;
import com.example.api.domain.entity.*;
public interface GetMyProfile { UserProfile execute(AuthenticatedUser user); }

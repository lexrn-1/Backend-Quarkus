package com.example.api.application.port.in;
import com.example.api.domain.entity.*;
public interface CompleteProfile { UserProfile execute(AuthenticatedUser user, ProfileDraft draft); }

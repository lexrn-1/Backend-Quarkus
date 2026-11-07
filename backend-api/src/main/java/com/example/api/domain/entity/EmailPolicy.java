package com.example.api.domain.entity;

import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import com.example.api.domain.exception.IdentityException;

public record EmailPolicy(Set<String> domains) {
    public EmailPolicy {
        domains = domains.stream().map(s -> s.strip().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }
    public void validate(String email) {
        if (email == null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IdentityException(IdentityException.Kind.INVALID_PROFILE, "La identidad debe tener un correo válido");
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (!domains.isEmpty() && !domains.contains(domain))
            throw new IdentityException(IdentityException.Kind.EMAIL_DOMAIN_NOT_ALLOWED, "Correo fuera de los dominios permitidos");
    }
}

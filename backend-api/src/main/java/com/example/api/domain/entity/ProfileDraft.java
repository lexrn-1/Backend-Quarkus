package com.example.api.domain.entity;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import com.example.api.domain.exception.IdentityException;
import static com.example.api.domain.exception.IdentityException.Kind.INVALID_PROFILE;

public record ProfileDraft(String firstName, String lastName, String username, String avatar) {
    public ProfileDraft {
        firstName = name(firstName); lastName = name(lastName);
        if (username == null) throw invalid("Username obligatorio");
        username = Normalizer.normalize(username.strip(), Normalizer.Form.NFKC);
        if (!username.matches("[A-Za-z0-9_]{3,20}"))
            throw invalid("Username: entre 3 y 20 letras ASCII, números o guion bajo");
        if (avatar == null || avatar.isBlank()) throw invalid("Avatar obligatorio");
        avatar = avatar.strip();
        if (!avatar.matches("avatar-[1-8]")) {
            try {
                var uri = URI.create(avatar);
                if (avatar.length() > 2048 || !"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getFragment() != null)
                    throw invalid("Avatar: seleccionar avatar-1 a avatar-8 o una URL HTTPS válida");
            } catch (IllegalArgumentException e) { throw invalid("URL de avatar inválida"); }
        }
    }
    public String usernameNormalized() { return username.toLowerCase(Locale.ROOT); }
    private static String name(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 80
                || value.codePoints().anyMatch(Character::isISOControl))
            throw invalid("Nombres y apellidos son obligatorios, hasta 80 caracteres cada uno");
        return value.strip();
    }
    private static IdentityException invalid(String message) { return new IdentityException(INVALID_PROFILE, message); }
}

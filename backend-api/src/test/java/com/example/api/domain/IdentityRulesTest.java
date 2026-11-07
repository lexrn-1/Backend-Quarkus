package com.example.api.domain;

import com.example.api.domain.entity.*;
import com.example.api.domain.exception.IdentityException;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class IdentityRulesTest {
    @Test void normalizesWithoutCaseCollisions() {
        var draft = new ProfileDraft(" Ana "," Pérez "," Student_01 ","avatar-1");
        assertEquals("student_01",draft.usernameNormalized());
        assertEquals("Ana",draft.firstName());
    }
    @Test void normalizesCompatibilityCharacters() {
        assertEquals("abc",new ProfileDraft("Ana","Pérez","ＡＢＣ","avatar-1").usernameNormalized());
    }
    @Test void rejectsInvalidNamesAndUsernames() {
        for (String username : new String[]{"a","x".repeat(21),"user/name","two names","áéí"})
            assertThrows(IdentityException.class,()->new ProfileDraft("Ana","Pérez",username,"avatar-1"));
        assertThrows(IdentityException.class,()->new ProfileDraft("\n","Pérez","valid","avatar-1"));
        assertThrows(IdentityException.class,()->new ProfileDraft("Ana\nTest","Pérez","valid","avatar-1"));
    }
    @Test void avatarIsSelectedOrSafeHttpsReference() {
        new ProfileDraft("Ana","Pérez","valid","https://example.org/avatar.png");
        for (String avatar : new String[]{"avatar-9","javascript:alert(1)","http://example.org/a","https://user:pass@example.org/a"})
            assertThrows(IdentityException.class,()->new ProfileDraft("Ana","Pérez","valid",avatar));
    }
    @Test void institutionalDomainUsesExactMatch() {
        var policy = new EmailPolicy(Set.of(" University.edu.co "));
        policy.validate("ana@UNIVERSITY.edu.co");
        for (String email : new String[]{"ana@evil-university.edu.co","ana@sub.university.edu.co","ana @university.edu.co","invalid"})
            assertThrows(IdentityException.class,()->policy.validate(email));
    }
    @Test void unsetInstitutionalPolicyStillRequiresAnEmail() {
        new EmailPolicy(Set.of()).validate("ana@example.org");
        assertThrows(IdentityException.class,()->new EmailPolicy(Set.of()).validate(null));
    }
}

package com.jobtrace.identityaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.application.PasswordResetStore;
import com.jobtrace.identityaccess.application.RequestPasswordReset;
import com.jobtrace.identityaccess.application.ResetPassword;
import com.jobtrace.identityaccess.application.ResetPassword.InvalidResetTokenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PasswordRecoveryIntegrationTest {
    private final MemoryStore store = new MemoryStore();
    private final List<String> delivered = new ArrayList<>();
    private final PasswordHasher hasher = new PasswordHasher() {
        @Override public String hash(String password) { return "hash:" + password; }
        @Override public boolean matches(String password, String hash) {
            return ("hash:" + password).equals(hash);
        }
    };
    private final RequestPasswordReset request = new RequestPasswordReset(store,
            (email, url) -> delivered.add(email + "|" + url), "https://jobtrace.test");
    private final ResetPassword reset = new ResetPassword(store, hasher);

    @Test
    void knownAndUnknownAddressesGetTheSamePublicOutcome() {
        request.execute("owner@example.test", "127.0.0.1");
        assertEquals(1, delivered.size());
        assertTrue(delivered.getFirst().matches("owner@example.test\\|"
                + "https://jobtrace.test/api/auth/reset-password/[A-Za-z0-9]{24}"
                + "\\?callbackURL=%2Freset-password"));
        request.execute("missing@example.test", "127.0.0.1");
        assertEquals(1, delivered.size());
        assertEquals(2, store.attempts);
    }

    @Test
    void tokenIsSingleUseAndInvalidTokenCannotMutate() {
        assertEquals("kW4Kkq2Pg7p8F2L5uPiAC2_-waW7ek7XNPF_g2EcuPc",
                RequestPasswordReset.identifier("abcdefghijklmnopqrstuvwx"));
        request.execute("owner@example.test", "127.0.0.1");
        String token = delivered.getFirst().split("/reset-password/")[1].split("\\?")[0];
        assertTrue(reset.valid(token));
        reset.execute(token, "New-Password");
        assertEquals("hash:New-Password", store.password);
        assertFalse(reset.valid(token));
        assertThrows(InvalidResetTokenException.class,
                () -> reset.execute(token, "Again-Pass"));
        assertEquals("hash:New-Password", store.password);
        assertThrows(InvalidResetTokenException.class,
                () -> reset.execute("invalid", "Again-Pass"));
    }

    @Test
    void deliveryFailureDoesNotRevealWhetherAddressExists() {
        RequestPasswordReset failing = new RequestPasswordReset(store,
                (email, url) -> { throw new IllegalStateException("offline"); },
                "https://jobtrace.test");
        failing.execute("owner@example.test", "127.0.0.1");
        failing.execute("missing@example.test", "127.0.0.1");
        assertEquals(2, store.attempts);
    }

    private static final class MemoryStore implements PasswordResetStore {
        int attempts;
        String identifier;
        String password = "old";
        @Override public void consumeAttempt(String source) { attempts++; }
        @Override public Optional<String> findVerifiedOwner(String email) {
            return "owner@example.test".equals(email)
                    ? Optional.of("owner") : Optional.empty();
        }
        @Override public void issue(String ownerId, String value) { identifier = value; }
        @Override public boolean valid(String value) { return value.equals(identifier); }
        @Override public boolean consumeAndReplace(String value, String hash) {
            if (!valid(value)) {
                return false;
            }
            identifier = null;
            password = hash;
            return true;
        }
    }
}

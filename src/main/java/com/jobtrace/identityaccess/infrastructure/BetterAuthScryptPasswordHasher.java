package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.stereotype.Component;

/** Better Auth 1.6-compatible scrypt representation: lowercase salt hex:key hex. */
@Component
public final class BetterAuthScryptPasswordHasher implements PasswordHasher {

    private static final int CPU_COST = 16_384;
    private static final int MEMORY_COST = 16;
    private static final int PARALLELIZATION = 1;
    private static final int KEY_LENGTH = 64;
    private static final int SALT_LENGTH = 16;
    private static final Pattern ENCODED = Pattern.compile("[0-9a-f]{32}:[0-9a-f]{128}");
    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom random;

    public BetterAuthScryptPasswordHasher() {
        this(new SecureRandom());
    }

    BetterAuthScryptPasswordHasher(SecureRandom random) {
        this.random = random;
    }

    @Override
    public boolean matches(String password, String encodedPassword) {
        if (password == null
                || encodedPassword == null
                || !ENCODED.matcher(encodedPassword).matches()) {
            throw new IllegalArgumentException("Invalid Better Auth password representation");
        }
        int delimiter = encodedPassword.indexOf(':');
        String salt = encodedPassword.substring(0, delimiter);
        byte[] expected = HEX.parseHex(encodedPassword.substring(delimiter + 1));
        byte[] actual = derive(password, salt);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        byte[] saltBytes = new byte[SALT_LENGTH];
        random.nextBytes(saltBytes);
        String salt = HEX.formatHex(saltBytes);
        return salt + ":" + HEX.formatHex(derive(password, salt));
    }

    private static byte[] derive(String password, String salt) {
        String normalized = Normalizer.normalize(password, Normalizer.Form.NFKC);
        return SCrypt.generate(
                normalized.getBytes(StandardCharsets.UTF_8),
                salt.getBytes(StandardCharsets.UTF_8),
                CPU_COST,
                MEMORY_COST,
                PARALLELIZATION,
                KEY_LENGTH);
    }
}

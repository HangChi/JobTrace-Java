package com.jobtrace.identityaccess.application;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Validates declared type, size and magic bytes before writing an owner-scoped object. */
@Service
public final class UploadAvatar {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png", "image/jpeg", "jpg",
            "image/webp", "webp", "image/gif", "gif");
    private final AvatarStore store;
    private final SecureRandom random = new SecureRandom();

    public UploadAvatar(AvatarStore store) {
        this.store = store;
    }

    public String execute(String ownerId, Input file) {
        if (ownerId == null || !ownerId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new InvalidAvatarException();
        }
        if (file == null || file.body() == null || file.body().length == 0
                || file.body().length > MAX_BYTES) {
            throw new InvalidAvatarException();
        }
        String type = file.contentType();
        String extension = EXTENSIONS.get(type);
        if (extension == null) {
            throw new InvalidAvatarException();
        }
        byte[] body = file.body();
        if (!signature(body, type)) {
            throw new InvalidAvatarException();
        }
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        return store.upload("avatars/" + ownerId + "/"
                + HexFormat.of().formatHex(nonce) + "." + extension, type, body);
    }

    private static boolean signature(byte[] bytes, String type) {
        if ("image/png".equals(type)) {
            return starts(bytes, new int[]{0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        }
        if ("image/jpeg".equals(type)) {
            return starts(bytes, new int[]{0xff, 0xd8, 0xff});
        }
        if ("image/gif".equals(type)) {
            return starts(bytes, "GIF87a".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                    || starts(bytes, "GIF89a".getBytes(
                            java.nio.charset.StandardCharsets.US_ASCII));
        }
        return starts(bytes, "RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                && bytes.length >= 12 && bytes[8] == 'W' && bytes[9] == 'E'
                && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static boolean starts(byte[] bytes, int[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xff) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    public static final class InvalidAvatarException extends RuntimeException {}
    public record Input(String contentType, byte[] body) {}
}

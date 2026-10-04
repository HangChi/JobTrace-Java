package com.jobtrace.identityaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobtrace.identityaccess.application.AvatarStore;
import com.jobtrace.identityaccess.application.UploadAvatar;
import com.jobtrace.identityaccess.application.UploadAvatar.InvalidAvatarException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AvatarUploadTest {
    private final AtomicInteger uploads = new AtomicInteger();
    private final AvatarStore store = (key, type, body) -> {
        uploads.incrementAndGet();
        assertTrue(key.matches("avatars/owner-1/[a-f0-9]{32}\\.png"));
        assertEquals("image/png", type);
        return "https://images.test/" + key;
    };
    private final UploadAvatar useCase = new UploadAvatar(store);
    private final byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47,
            0x0d, 0x0a, 0x1a, 0x0a};

    @Test
    void validImageGetsOwnerScopedUrl() {
        String url = useCase.execute("owner-1", image("image/png", png));
        assertTrue(url.startsWith("https://images.test/avatars/owner-1/"));
        assertEquals(1, uploads.get());
    }

    @Test
    void malformedAndOversizedImagesNeverReachStorage() {
        assertThrows(InvalidAvatarException.class,
                () -> useCase.execute("owner-1", image("image/jpeg", png)));
        assertThrows(InvalidAvatarException.class,
                () -> useCase.execute("../other", image("image/png", png)));
        assertThrows(InvalidAvatarException.class,
                () -> useCase.execute("owner-1", image("image/png", new byte[5 * 1024 * 1024 + 1])));
        assertEquals(0, uploads.get());
    }

    @Test
    void storageFailureIsNotReportedAsSuccess() {
        UploadAvatar failing = new UploadAvatar((key, type, body) -> {
            throw new IllegalStateException("storage unavailable");
        });
        assertThrows(IllegalStateException.class,
                () -> failing.execute("owner-1", image("image/png", png)));
    }

    @Test
    void acceptsAllSupportedSignaturesAndExactSizeLimit() {
        UploadAvatar accepting = new UploadAvatar((key, type, body) -> {
            assertTrue(key.startsWith("avatars/owner-1/"));
            return "https://images.test/" + key;
        });
        byte[] atLimit = new byte[UploadAvatar.MAX_BYTES];
        System.arraycopy(png, 0, atLimit, 0, png.length);
        assertTrue(accepting.execute("owner-1", image("image/png", atLimit)).endsWith(".png"));
        assertTrue(accepting.execute("owner-1", image("image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})).endsWith(".jpg"));
        assertTrue(accepting.execute("owner-1", image("image/gif",
                "GIF89a".getBytes(java.nio.charset.StandardCharsets.US_ASCII))).endsWith(".gif"));
        byte[] webp = new byte[12];
        System.arraycopy("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                0, webp, 8, 4);
        assertTrue(accepting.execute("owner-1", image("image/webp", webp)).endsWith(".webp"));
    }

    private static UploadAvatar.Input image(String type, byte[] bytes) {
        return new UploadAvatar.Input(type, bytes);
    }
}

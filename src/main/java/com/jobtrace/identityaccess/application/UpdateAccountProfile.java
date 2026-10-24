package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountProfileNotFoundException;
import com.jobtrace.identityaccess.domain.AccountProfileResult;
import com.jobtrace.identityaccess.domain.AccountProfileUpdate;
import java.net.URI;
import org.springframework.stereotype.Service;

@Service
public class UpdateAccountProfile {

    private final AccountProfileCommand command;

    public UpdateAccountProfile(AccountProfileCommand command) {
        this.command = command;
    }

    public AccountProfileResult execute(String ownerId, String displayName, String image) {
        if (ownerId == null || ownerId.isBlank() || ownerId.length() > 128) {
            throw new IllegalArgumentException("ownerId must contain 1 through 128 characters");
        }
        if (displayName == null) {
            throw new IllegalArgumentException("displayName is required");
        }
        String normalizedName = displayName.trim();
        if (normalizedName.isEmpty() || normalizedName.length() > 100) {
            throw new IllegalArgumentException("displayName must contain 1 through 100 characters");
        }
        String normalizedImage = normalizeImage(image);
        return command.update(new AccountProfileUpdate(ownerId, normalizedName, normalizedImage))
                .orElseThrow(AccountProfileNotFoundException::new);
    }

    private static String normalizeImage(String image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        try {
            URI value = URI.create(image);
            if (!value.isAbsolute() || value.getScheme().isBlank()) {
                throw new IllegalArgumentException("image must be an absolute URL");
            }
            return image;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("image must be an absolute URL", exception);
        }
    }
}

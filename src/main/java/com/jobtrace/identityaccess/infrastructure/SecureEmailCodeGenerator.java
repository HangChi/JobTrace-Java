package com.jobtrace.identityaccess.infrastructure;

import com.jobtrace.identityaccess.application.AccountEmailCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public final class SecureEmailCodeGenerator implements AccountEmailCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}

package com.jobtrace.identityaccess.application;

import org.springframework.stereotype.Service;

/** Issues a registration-purpose code for an unclaimed address. */
@Service
public class RequestRegistrationEmailCode {

    private final RegistrationEmailCodeStore store;
    private final AccountEmailCodeGenerator generator;
    private final RegistrationEmailCodeHasher hasher;
    private final AccountEmailCodeDelivery delivery;

    public RequestRegistrationEmailCode(RegistrationEmailCodeStore store,
            AccountEmailCodeGenerator generator, RegistrationEmailCodeHasher hasher,
            AccountEmailCodeDelivery delivery) {
        this.store = store;
        this.generator = generator;
        this.hasher = hasher;
        this.delivery = delivery;
    }

    public void execute(String email) {
        String normalized = EmailCodeAddress.normalize(email);
        delivery.ensureAvailable();
        store.consumeSourceAttempt();
        String code = generator.generate();
        store.issue(normalized, hasher.hash(normalized, code));
        delivery.deliver(normalized, code);
    }
}

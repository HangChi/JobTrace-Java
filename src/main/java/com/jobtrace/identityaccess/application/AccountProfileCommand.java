package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountProfileResult;
import com.jobtrace.identityaccess.domain.AccountProfileUpdate;
import java.util.Optional;

@FunctionalInterface
public interface AccountProfileCommand {

    Optional<AccountProfileResult> update(AccountProfileUpdate command);
}

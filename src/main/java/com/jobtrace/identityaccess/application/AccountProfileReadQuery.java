package com.jobtrace.identityaccess.application;

import com.jobtrace.identityaccess.domain.AccountProfileResult;
import java.util.Optional;

/** Reads only the authenticated owner's safe profile fields. */
public interface AccountProfileReadQuery {
    Optional<AccountProfileResult> findByOwner(String ownerId);
}

package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.AccountProfileReadQuery;
import com.jobtrace.identityaccess.domain.AccountProfileResult;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the safe profile projection to the verified owner. */
@RestController
public class AccountProfileReadController {
    private final AccountProfileReadQuery profiles;

    public AccountProfileReadController(AccountProfileReadQuery profiles) {
        this.profiles = profiles;
    }

    @GetMapping(AccountProfileController.PATH)
    public ResponseEntity<AccountProfileResult> current(Principal principal) {
        String owner = BridgePrincipalOwner.require(principal);
        AccountProfileResult profile = profiles.findByOwner(owner).orElseThrow(() ->
                new Problem("not_found", "没有找到账号资料。", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(profile);
    }
}

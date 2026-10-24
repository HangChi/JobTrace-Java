package com.jobtrace.shared.security;

import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the existing session-backed CSRF token to an authenticated same-origin browser. */
@RestController
public class CsrfTokenController {
    @GetMapping("/api/security/csrf")
    public ResponseEntity<Map<String, String>> current(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("token", token.getToken()));
    }
}

package com.jobtrace.jobmarket.web;

import com.jobtrace.identityaccess.web.BridgePrincipalAdmin;
import com.jobtrace.jobmarket.application.SourceSyncStore;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/job-market/sync-runs")
public final class JobMarketSyncAdminController {
    private final SourceSyncStore sync;

    public JobMarketSyncAdminController(SourceSyncStore sync) {
        this.sync = sync;
    }

    @GetMapping
    public ResponseEntity<SourceSyncStore.RunPage> history(
            @RequestParam(required = false) UUID sourceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit, Principal principal) {
        BridgePrincipalAdmin.require(principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(sync.history(sourceId, page, limit));
    }
}

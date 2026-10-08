package com.jobtrace.jobmarket.web;

import com.jobtrace.identityaccess.web.BridgePrincipalAdmin;
import com.jobtrace.jobmarket.application.JobMarketCatalogQuery;
import java.security.Principal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/job-market/catalog")
public final class JobMarketCatalogAdminController {
    private final JobMarketCatalogQuery catalog;

    public JobMarketCatalogAdminController(JobMarketCatalogQuery catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public ResponseEntity<JobMarketCatalogQuery.Page> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        BridgePrincipalAdmin.require(principal);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(catalog.list(q, page, limit));
    }
}

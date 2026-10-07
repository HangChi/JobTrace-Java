package com.jobtrace.analytics.web;

import com.jobtrace.analytics.application.GetAnalyticsReport;
import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnalyticsReportController {

    private final GetAnalyticsReport report;

    public AnalyticsReportController(GetAnalyticsReport report) {
        this.report = report;
    }

    @GetMapping("/api/analytics/report")
    ResponseEntity<Map<String, Object>> get(@RequestParam Map<String, String> parameters,
            Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(report.execute(BridgePrincipalOwner.require(principal), parameters));
    }
}

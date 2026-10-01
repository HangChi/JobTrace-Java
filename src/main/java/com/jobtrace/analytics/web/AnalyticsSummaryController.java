package com.jobtrace.analytics.web;

import com.jobtrace.analytics.application.GetAnalyticsSummary;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsSummaryController {

    private final GetAnalyticsSummary getAnalyticsSummary;

    public AnalyticsSummaryController(GetAnalyticsSummary getAnalyticsSummary) {
        this.getAnalyticsSummary = getAnalyticsSummary;
    }

    @GetMapping("/summary")
    ResponseEntity<GetAnalyticsSummary.AnalyticsSummary> summary(Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(getAnalyticsSummary.execute(principal.getName()));
    }
}

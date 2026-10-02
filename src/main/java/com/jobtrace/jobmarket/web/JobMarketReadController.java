package com.jobtrace.jobmarket.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.jobmarket.application.GetCampaignDetail;
import com.jobtrace.jobmarket.application.ListCampaigns;
import com.jobtrace.jobmarket.domain.CampaignDetail;
import com.jobtrace.jobmarket.domain.CampaignPage;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import java.time.Duration;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-market/campaigns")
public class JobMarketReadController {

    private final ListCampaigns listCampaigns;
    private final GetCampaignDetail getCampaignDetail;
    private final JobMarketReadMetrics metrics;

    public JobMarketReadController(
            ListCampaigns listCampaigns,
            GetCampaignDetail getCampaignDetail,
            JobMarketReadMetrics metrics) {
        this.listCampaigns = listCampaigns;
        this.getCampaignDetail = getCampaignDetail;
        this.metrics = metrics;
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignDetail> detail(
            @PathVariable UUID campaignId, Principal principal) {
        long started = System.nanoTime();
        try {
            CampaignDetail detail = getCampaignDetail.execute(
                    BridgePrincipalOwner.require(principal), campaignId);
            record("detail", "success", started);
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(detail);
        } catch (RuntimeException exception) {
            record("detail", outcome(exception), started);
            throw exception;
        }
    }

    @GetMapping
    public ResponseEntity<CampaignPage> list(
            @RequestParam MultiValueMap<String, String> parameters, Principal principal) {
        long started = System.nanoTime();
        try {
            CampaignPage page = listCampaigns.execute(
                    BridgePrincipalOwner.require(principal),
                    MarketplaceQueryParameters.from(parameters));
            record("list", "success", started);
            return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(page);
        } catch (RuntimeException exception) {
            record("list", outcome(exception), started);
            throw exception;
        }
    }

    private void record(String operation, String outcome, long started) {
        metrics.record(operation, outcome, Duration.ofNanos(System.nanoTime() - started));
    }

    private static String outcome(RuntimeException exception) {
        if (exception instanceof Problem problem && problem.status() == HttpStatus.UNAUTHORIZED) {
            return "denied_identity";
        }
        if (exception instanceof Problem problem && problem.status() == HttpStatus.NOT_FOUND) {
            return "not_found";
        }
        if (exception instanceof IllegalArgumentException) {
            return "invalid";
        }
        return exception instanceof DataAccessException ? "dependency_failure" : "failure";
    }
}

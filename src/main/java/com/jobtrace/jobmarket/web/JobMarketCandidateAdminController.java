package com.jobtrace.jobmarket.web;

import com.jobtrace.identityaccess.web.BridgePrincipalAdmin;
import com.jobtrace.jobmarket.application.CandidateReviewStore;
import com.jobtrace.jobmarket.application.ManageJobMarketCandidates;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/job-market")
public final class JobMarketCandidateAdminController {
    private final ManageJobMarketCandidates candidates;

    public JobMarketCandidateAdminController(ManageJobMarketCandidates candidates) {
        this.candidates = candidates;
    }

    @GetMapping("/discovery")
    public ResponseEntity<CandidateReviewStore.CandidateList> sources(
            @RequestParam(required = false) String status, Principal principal) {
        BridgePrincipalAdmin.require(principal);
        return noStore(candidates.sources(status));
    }

    @PatchMapping("/discovery/{candidateId}")
    public ResponseEntity<Map<String, Object>> reviewSource(@PathVariable UUID candidateId,
            @RequestBody(required = false) Map<String, String> body, Principal principal) {
        BridgePrincipalAdmin.require(principal);
        return noStore(candidates.reviewSource(candidateId,
                body == null ? null : body.get("action")));
    }

    @GetMapping("/company-candidates")
    public ResponseEntity<CandidateReviewStore.CandidateList> companies(
            @RequestParam(required = false) String status, Principal principal) {
        BridgePrincipalAdmin.require(principal);
        return noStore(candidates.companies(status));
    }

    @PatchMapping("/company-candidates/{candidateId}")
    public ResponseEntity<Map<String, Object>> reviewCompany(@PathVariable UUID candidateId,
            @RequestBody(required = false) Map<String, String> body, Principal principal) {
        BridgePrincipalAdmin.require(principal);
        var overrides = body == null ? null : new CandidateReviewStore.CompanyOverrides(
                body.get("companyName"), body.get("companyType"), body.get("industry"));
        return noStore(candidates.reviewCompany(candidateId,
                body == null ? null : body.get("action"), overrides));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }
}

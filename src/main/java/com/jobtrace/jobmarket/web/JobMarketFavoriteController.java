package com.jobtrace.jobmarket.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.jobmarket.application.SetCampaignFavorite;
import com.jobtrace.jobmarket.application.SetCampaignFavorite.FavoriteState;
import com.jobtrace.shared.web.Problem;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-market/campaigns/{campaignId}/favorite")
public class JobMarketFavoriteController {

    private final SetCampaignFavorite setFavorite;

    public JobMarketFavoriteController(SetCampaignFavorite setFavorite) {
        this.setFavorite = setFavorite;
    }

    @PutMapping
    public ResponseEntity<FavoriteState> favorite(
            @PathVariable UUID campaignId, Principal principal) {
        return update(campaignId, principal, true);
    }

    @DeleteMapping
    public ResponseEntity<FavoriteState> unfavorite(
            @PathVariable UUID campaignId, Principal principal) {
        return update(campaignId, principal, false);
    }

    private ResponseEntity<FavoriteState> update(
            UUID campaignId, Principal principal, boolean favorite) {
        String ownerId = BridgePrincipalOwner.require(principal);
        FavoriteState state = setFavorite.execute(ownerId, campaignId, favorite)
                .orElseThrow(() -> new Problem("not_found", "没有找到这条招聘记录。",
                        HttpStatus.NOT_FOUND));
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(state);
    }
}

package com.jobtrace.analytics.web;

import com.jobtrace.analytics.application.ProgressReminderResolution;
import com.jobtrace.analytics.application.ProgressReminderResolution.Resolution;
import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class ProgressReminderResolutionController {

    private final ProgressReminderResolution resolution;

    public ProgressReminderResolutionController(ProgressReminderResolution resolution) {
        this.resolution = resolution;
    }

    @PostMapping("/api/analytics/progress-reminders/{stageOccurrenceId}/complete")
    ResponseEntity<ProgressReminderResolution.Result> complete(
            @PathVariable UUID stageOccurrenceId, Principal principal) {
        return resolve(stageOccurrenceId, Resolution.COMPLETED, principal);
    }

    @PostMapping("/api/analytics/progress-reminders/{stageOccurrenceId}/resolve")
    ResponseEntity<ProgressReminderResolution.Result> resolve(
            @PathVariable UUID stageOccurrenceId,
            @RequestBody Map<String, JsonNode> body,
            Principal principal) {
        JsonNode value = body == null ? null : body.get("resolution");
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("resolution is required");
        }
        return resolve(stageOccurrenceId, Resolution.fromWire(value.textValue()), principal);
    }

    private ResponseEntity<ProgressReminderResolution.Result> resolve(
            UUID stageOccurrenceId, Resolution value, Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(resolution.resolve(BridgePrincipalOwner.require(principal), stageOccurrenceId, value));
    }
}

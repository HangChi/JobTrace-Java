package com.jobtrace.reminders.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.reminders.application.DeliverDueReminders;
import com.jobtrace.reminders.application.DeliverDueReminders.Result;
import com.jobtrace.reminders.domain.ReminderOperationException;
import com.jobtrace.reminders.domain.ReminderOperationException.Kind;
import com.jobtrace.shared.web.Problem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class ReminderDeliveryController {

    private final DeliverDueReminders delivery;
    private final String secret;

    public ReminderDeliveryController(DeliverDueReminders delivery,
            @Value("${jobtrace.reminders.delivery-secret:}") String secret) {
        this.delivery = delivery;
        this.secret = secret;
    }

    @PostMapping("/api/reminders/{id}/retry-email")
    ResponseEntity<Map<String, Boolean>> retry(@PathVariable UUID id,
            @RequestBody JsonNode body, Principal principal) {
        int version = ReminderCommandRequest.version(body);
        delivery.retry(BridgePrincipalOwner.require(principal), id, version);
        return ResponseEntity.status(202).cacheControl(CacheControl.noStore())
                .body(Map.of("accepted", true));
    }

    @PostMapping("/api/internal/reminders/deliver")
    ResponseEntity<Result> deliver(@RequestHeader(value = "authorization", required = false)
            String authorization, @RequestBody(required = false) JsonNode body) {
        if (!authorized(authorization)) {
            throw new Problem("unauthorized", "提醒调度凭据无效。", HttpStatus.UNAUTHORIZED);
        }
        Integer limit = null;
        if (body != null) {
            if (!body.isObject()) {
                throw new ReminderOperationException(Kind.VALIDATION, "投递批量无效。");
            }
            JsonNode raw = body.get("limit");
            if (raw != null) {
                if (!raw.isIntegralNumber() || !raw.toString().matches("[1-9][0-9]{0,2}")
                        || Integer.parseInt(raw.toString()) > 100) {
                    throw new ReminderOperationException(Kind.VALIDATION, "投递批量无效。");
                }
                limit = raw.intValue();
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(delivery.deliver(limit));
    }

    private boolean authorized(String authorization) {
        if (secret == null || secret.length() < 32 || authorization == null
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        byte[] supplied = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
        byte[] expected = secret.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(supplied, expected);
    }
}

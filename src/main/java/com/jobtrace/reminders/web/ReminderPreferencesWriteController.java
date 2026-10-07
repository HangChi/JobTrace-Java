package com.jobtrace.reminders.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.reminders.application.ReminderPreferencesStore;
import com.jobtrace.reminders.domain.ReminderOperationException;
import com.jobtrace.reminders.domain.ReminderOperationException.Kind;
import com.jobtrace.reminders.domain.ReminderPreferences;
import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class ReminderPreferencesWriteController {

    private final ReminderPreferencesStore store;

    public ReminderPreferencesWriteController(ReminderPreferencesStore store) {
        this.store = store;
    }

    @PatchMapping("/api/reminder-settings")
    ResponseEntity<ReminderPreferences> update(@RequestBody JsonNode body, Principal principal) {
        ReminderPreferences value = parse(body);
        ReminderPreferences updated = store.update(BridgePrincipalOwner.require(principal), value)
                .orElseThrow(() -> new ReminderOperationException(Kind.NOT_FOUND,
                        "没有找到账号资料。"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(updated);
    }

    private static ReminderPreferences parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid();
        }
        JsonNode enabled = body.get("homeEnabled");
        JsonNode view = body.get("homeView");
        JsonNode lead = body.get("defaultLead");
        JsonNode snooze = body.get("defaultSnoozeMinutes");
        JsonNode email = body.get("emailDefault");
        if (enabled == null || !enabled.isBoolean() || view == null || !view.isTextual()
                || lead == null || !lead.isTextual() || snooze == null
                || !snooze.isIntegralNumber()
                || !("30".equals(snooze.toString()) || "60".equals(snooze.toString())
                    || "1440".equals(snooze.toString()))
                || email == null || !email.isBoolean()) {
            throw invalid();
        }
        try {
            return new ReminderPreferences(enabled.booleanValue(), view.textValue(),
                    lead.textValue(), snooze.intValue(), email.booleanValue());
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static ReminderOperationException invalid() {
        return new ReminderOperationException(Kind.VALIDATION, "提醒设置无效。");
    }
}

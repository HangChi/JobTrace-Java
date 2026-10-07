package com.jobtrace.reminders.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.reminders.application.ReminderCommands;
import com.jobtrace.reminders.domain.Reminder;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/reminders")
public class ReminderCommandController {

    private final ReminderCommands commands;

    public ReminderCommandController(ReminderCommands commands) {
        this.commands = commands;
    }

    @PostMapping
    ResponseEntity<Reminder> create(@RequestBody JsonNode body, Principal principal) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(commands.create(BridgePrincipalOwner.require(principal),
                        ReminderCommandRequest.create(body)));
    }

    @PatchMapping("/{id}")
    ResponseEntity<Reminder> update(@PathVariable UUID id, @RequestBody JsonNode body,
            Principal principal) {
        return ok(commands.update(BridgePrincipalOwner.require(principal), id,
                ReminderCommandRequest.update(body)));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Reminder> cancel(@PathVariable UUID id, @RequestBody JsonNode body,
            Principal principal) {
        return transition(id, body, principal, "cancel");
    }

    @PostMapping("/{id}/complete")
    ResponseEntity<Reminder> complete(@PathVariable UUID id, @RequestBody JsonNode body,
            Principal principal) {
        return transition(id, body, principal, "complete");
    }

    @PostMapping("/{id}/snooze")
    ResponseEntity<Reminder> snooze(@PathVariable UUID id, @RequestBody JsonNode body,
            Principal principal) {
        return ok(commands.transition(BridgePrincipalOwner.require(principal), id,
                ReminderCommandRequest.snooze(body)));
    }

    @PostMapping("/{id}/reopen")
    ResponseEntity<Reminder> reopen(@PathVariable UUID id, @RequestBody JsonNode body,
            Principal principal) {
        return ok(commands.transition(BridgePrincipalOwner.require(principal), id,
                ReminderCommandRequest.reopen(body)));
    }

    private ResponseEntity<Reminder> transition(UUID id, JsonNode body,
            Principal principal, String action) {
        return ok(commands.transition(BridgePrincipalOwner.require(principal), id,
                ReminderCommandRequest.versionOnly(body, action)));
    }

    private ResponseEntity<Reminder> ok(Reminder reminder) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reminder);
    }
}

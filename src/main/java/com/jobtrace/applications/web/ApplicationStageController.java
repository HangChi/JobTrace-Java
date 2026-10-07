package com.jobtrace.applications.web;

import com.jobtrace.applications.application.ApplicationStageCommands;
import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
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
@RequestMapping("/api/applications/{id}/stages")
public class ApplicationStageController {

    private final ApplicationStageCommands commands;

    public ApplicationStageController(ApplicationStageCommands commands) {
        this.commands = commands;
    }

    @PostMapping
    ResponseEntity<ApplicationDetail> add(@PathVariable UUID id,
            @RequestBody JsonNode body, Principal principal) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(commands.add(BridgePrincipalOwner.require(principal), id,
                        ApplicationCommandRequest.stage(body)));
    }

    @PatchMapping("/{occurrenceId}")
    ResponseEntity<ApplicationDetail> update(@PathVariable UUID id, @PathVariable UUID occurrenceId,
            @RequestBody JsonNode body, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(commands.update(BridgePrincipalOwner.require(principal), id, occurrenceId,
                        ApplicationCommandRequest.version(body),
                        ApplicationCommandRequest.stage(body),
                        ApplicationCommandRequest.changeDate(body)));
    }

    @DeleteMapping("/{occurrenceId}")
    ResponseEntity<ApplicationDetail> remove(@PathVariable UUID id, @PathVariable UUID occurrenceId,
            @RequestBody JsonNode body, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(commands.remove(BridgePrincipalOwner.require(principal), id, occurrenceId,
                        ApplicationCommandRequest.changeDate(body)));
    }
}

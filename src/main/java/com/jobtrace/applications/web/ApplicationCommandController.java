package com.jobtrace.applications.web;

import com.jobtrace.applications.application.ApplicationCommands;
import com.jobtrace.applications.domain.ApplicationDetail;
import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import java.security.Principal;
import java.util.Map;
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
@RequestMapping("/api/applications")
public class ApplicationCommandController {

    private final ApplicationCommands commands;

    public ApplicationCommandController(ApplicationCommands commands) {
        this.commands = commands;
    }

    @PostMapping
    ResponseEntity<ApplicationDetail> create(@RequestBody JsonNode body, Principal principal) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(commands.create(BridgePrincipalOwner.require(principal),
                        ApplicationCommandRequest.create(body)));
    }

    @PatchMapping("/{id}")
    ResponseEntity<ApplicationDetail> update(@PathVariable UUID id,
            @RequestBody JsonNode body, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(commands.update(BridgePrincipalOwner.require(principal), id,
                        ApplicationCommandRequest.update(body)));
    }

    @PatchMapping("/{id}/status")
    ResponseEntity<?> updateStatus(@PathVariable UUID id,
            @RequestBody JsonNode body, Principal principal) {
        var status = ApplicationCommandRequest.status(body);
        int version = ApplicationCommandRequest.version(body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(commands.updateStatus(BridgePrincipalOwner.require(principal),
                        id, version, status));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        commands.delete(BridgePrincipalOwner.require(principal), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping
    ResponseEntity<Map<String, Integer>> deleteMany(@RequestBody JsonNode body, Principal principal) {
        int count = commands.deleteMany(BridgePrincipalOwner.require(principal),
                ApplicationCommandRequest.ids(body));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("deletedCount", count));
    }
}

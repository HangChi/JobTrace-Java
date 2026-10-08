package com.jobtrace.interviews.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.interviews.application.InterviewCommands;
import com.jobtrace.interviews.domain.InterviewDetail;
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
@RequestMapping("/api/interviews")
public class InterviewCommandController {

    private final InterviewCommands commands;

    public InterviewCommandController(InterviewCommands commands) {
        this.commands = commands;
    }

    @PostMapping
    ResponseEntity<InterviewDetail> create(@RequestBody JsonNode body, Principal principal) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(commands.create(BridgePrincipalOwner.require(principal),
                        InterviewCommandRequest.create(body)));
    }

    @PatchMapping("/{id}")
    ResponseEntity<InterviewDetail> update(@PathVariable UUID id,
            @RequestBody JsonNode body, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(commands.update(BridgePrincipalOwner.require(principal), id,
                        InterviewCommandRequest.update(body)));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
        commands.delete(BridgePrincipalOwner.require(principal), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}

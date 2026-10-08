package com.jobtrace.interviews.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.interviews.application.PublicInterviews;
import com.jobtrace.interviews.domain.PublicInterviewPage;
import com.jobtrace.interviews.domain.PublicInterviewPage.Item;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews/public")
public class PublicInterviewController {

    private final PublicInterviews interviews;

    public PublicInterviewController(PublicInterviews interviews) {
        this.interviews = interviews;
    }

    @GetMapping
    ResponseEntity<PublicInterviewPage> list(
            @RequestParam MultiValueMap<String, String> parameters, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(interviews.list(BridgePrincipalOwner.require(principal),
                        PublicInterviewListParameters.from(parameters)));
    }

    @GetMapping("/{id}")
    ResponseEntity<Item> detail(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(interviews.detail(BridgePrincipalOwner.require(principal), id));
    }
}

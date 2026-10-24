package com.jobtrace.interviews.web;

import com.jobtrace.identityaccess.web.BridgePrincipalOwner;
import com.jobtrace.interviews.application.PublicInterviewEngagement;
import com.jobtrace.interviews.domain.PublicInterviewPage.Comment;
import com.jobtrace.interviews.domain.PublicInterviewPage.CommentResult;
import com.jobtrace.interviews.domain.PublicInterviewPage.Engagement;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/interviews/public/{id}")
public class PublicInterviewEngagementController {

    private final PublicInterviewEngagement engagement;

    public PublicInterviewEngagementController(PublicInterviewEngagement engagement) {
        this.engagement = engagement;
    }

    @PostMapping("/like")
    ResponseEntity<Engagement> like(@PathVariable UUID id, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(engagement.toggleLike(BridgePrincipalOwner.require(principal), id));
    }

    @GetMapping("/comments")
    ResponseEntity<List<Comment>> comments(@PathVariable UUID id, Principal principal) {
        BridgePrincipalOwner.require(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(engagement.listComments(id));
    }

    @PostMapping("/comments")
    ResponseEntity<CommentResult> comment(@PathVariable UUID id,
            @RequestBody JsonNode body, Principal principal) {
        if (body == null || !body.isObject() || body.get("content") == null
                || !body.get("content").isTextual()) {
            throw new IllegalArgumentException("评论内容无效。");
        }
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(engagement.addComment(BridgePrincipalOwner.require(principal),
                        id, body.get("content").textValue()));
    }
}

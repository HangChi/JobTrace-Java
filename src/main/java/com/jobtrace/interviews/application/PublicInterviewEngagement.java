package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.PublicInterviewPage.Comment;
import com.jobtrace.interviews.domain.PublicInterviewPage.CommentResult;
import com.jobtrace.interviews.domain.PublicInterviewPage.Engagement;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PublicInterviewEngagement {

    private final PublicInterviewEngagementStore store;

    public PublicInterviewEngagement(PublicInterviewEngagementStore store) {
        this.store = store;
    }

    public Engagement toggleLike(String viewerId, UUID id) {
        return store.toggleLike(viewerId, id);
    }

    public CommentResult addComment(String viewerId, UUID id, String content) {
        if (content == null || content.trim().isEmpty() || content.trim().length() > 1000) {
            throw new IllegalArgumentException("评论内容长度必须为 1–1000 字符。");
        }
        return store.addComment(viewerId, id, content.trim());
    }

    public List<Comment> listComments(UUID id) {
        return store.listComments(id);
    }
}

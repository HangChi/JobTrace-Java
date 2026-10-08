package com.jobtrace.interviews.application;

import com.jobtrace.interviews.domain.PublicInterviewPage.Comment;
import com.jobtrace.interviews.domain.PublicInterviewPage.CommentResult;
import com.jobtrace.interviews.domain.PublicInterviewPage.Engagement;
import java.util.List;
import java.util.UUID;

public interface PublicInterviewEngagementStore {
    Engagement toggleLike(String viewerId, UUID id);
    CommentResult addComment(String viewerId, UUID id, String content);
    List<Comment> listComments(UUID id);
}

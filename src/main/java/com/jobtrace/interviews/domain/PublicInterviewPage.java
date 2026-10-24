package com.jobtrace.interviews.domain;

import java.time.LocalDate;
import java.util.List;

/** Explicit public-only response shape; private review records must never be serialized here. */
public record PublicInterviewPage(List<Item> items, String nextCursor, int total,
        int limit, Facets facets) {

    public PublicInterviewPage {
        items = List.copyOf(items);
    }

    public record Facets(List<String> cities, List<String> positions) {
        public Facets {
            cities = List.copyOf(cities);
            positions = List.copyOf(positions);
        }
    }

    public record Author(String username, String image) {}

    public record Engagement(int likeCount, int commentCount, int viewCount,
            boolean likedByViewer) {}

    public record Comment(String id, String content, String createdAt, Author author) {}

    public record CommentResult(Comment comment, Engagement engagement) {}

    public record Question(String category, String question, String originalAnswer,
            String followUpNotes, String improvedAnswer) {}

    public record Item(String id, String companyName, String positionName, String city,
            String stage, LocalDate interviewedOn, String publishedAt, int questionCount,
            Author author, Engagement engagement, String highlights, String gaps,
            List<Question> questions, List<Comment> recentComments) {
        public Item {
            questions = List.copyOf(questions);
            recentComments = List.copyOf(recentComments);
        }
    }
}

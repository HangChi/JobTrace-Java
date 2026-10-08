package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.interviews.application.PublicInterviewEngagement;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicInterviewEngagementTest extends PublicInterviewDatabaseTest {

    @Autowired MockMvc mvc;

    @Test
    void togglesLikeAndReturnsViewerSpecificEngagement() throws Exception {
        mvc.perform(post("/api/interviews/public/{id}/like", R102)
                        .with(asOwner(OTHER_OWNER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedByViewer").value(true))
                .andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(post("/api/interviews/public/{id}/like", R102)
                        .with(asOwner(OTHER_OWNER)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedByViewer").value(false))
                .andExpect(jsonPath("$.likeCount").value(0));
        assertThat(jdbc.queryForObject("select count(*) from interview_review_likes",
                Integer.class)).isZero();
    }

    @Test
    void addsTrimmedCommentAndListsNewestFirst() throws Exception {
        mvc.perform(post("/api/interviews/public/{id}/comments", R102)
                        .with(asOwner(OTHER_OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  Thanks for sharing!  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment.content").value("Thanks for sharing!"))
                .andExpect(jsonPath("$.engagement.commentCount").value(2));
        mvc.perform(get("/api/interviews/public/{id}/comments", R102)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Thanks for sharing!"))
                .andExpect(jsonPath("$[1].content").value("Useful!"));
    }

    @Test
    void rejectsInvalidContentAndUnavailableReviewsWithoutMutation() throws Exception {
        for (String content : new String[] {"", "   ", "a".repeat(1001)}) {
            mvc.perform(post("/api/interviews/public/{id}/comments", R102)
                            .with(asOwner(OTHER_OWNER)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"" + content + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        jdbc.update("""
                update interview_reviews set visibility='private',published_at=null
                where id=?::uuid
                """, R102);
        mvc.perform(post("/api/interviews/public/{id}/like", R102)
                        .with(asOwner(OTHER_OWNER)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/interviews/public/{id}/comments", R102)
                        .with(asOwner(OTHER_OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Not allowed\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/interviews/public/{id}/comments", R102)
                        .with(asOwner(OTHER_OWNER)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from interview_review_likes",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from interview_review_comments",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsMissingOrNonTextCommentPayload() throws Exception {
        for (String payload : new String[] {"{}", "[]", "{\"content\":null}",
                "{\"content\":42}"}) {
            mvc.perform(post("/api/interviews/public/{id}/comments", R102)
                            .with(asOwner(OTHER_OWNER)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from interview_review_comments",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void serviceRejectsNullCommentBeforePersistence() {
        assertThatThrownBy(() -> new PublicInterviewEngagement(null)
                .addComment(OWNER, UUID.fromString(R102), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commentsAreBoundedAndEmptyPublicListIsAllowed() throws Exception {
        mvc.perform(get("/api/interviews/public/{id}/comments", R201)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        jdbc.update("""
                insert into interview_review_comments(interview_review_id,user_id,content,created_at)
                select ?::uuid,'owner-a','Comment ' || sequence,
                  '2026-09-24T00:00:00Z'::timestamptz + sequence * interval '1 minute'
                from generate_series(1,55) sequence
                """, R102);
        mvc.perform(get("/api/interviews/public/{id}/comments", R102)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(50))
                .andExpect(jsonPath("$[0].content").value("Comment 55"));
        mvc.perform(get("/api/interviews/public/{id}/comments", R101)
                        .with(asOwner(OWNER)))
                .andExpect(status().isNotFound());
    }
}

package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.interviews.domain.PublicInterviewCriteria;
import com.jobtrace.interviews.domain.PublicInterviewCriteria.Sort;
import com.jobtrace.interviews.domain.PublicInterviewCursorCodec;
import com.jobtrace.interviews.infrastructure.PostgresPublicInterviewQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PublicInterviewReadIntegrationTest extends PublicInterviewDatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired PostgresPublicInterviewQuery query;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void repositoryReturnsSafePage() {
        assertThat(query.list(OWNER, new PublicInterviewCriteria("", "", "", Sort.LATEST,
                null, 20)).items()).hasSize(2);
    }

    @Test
    void feedPaginatesFiltersAndNeverSerializesPrivateFields() throws Exception {
        String first = mvc.perform(get("/api/interviews/public").with(asOwner(OWNER))
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value(R201))
                .andExpect(jsonPath("$.items[0].author.username").value("Bob"))
                .andExpect(jsonPath("$.facets.cities.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        var page = mapper.readTree(first);
        String cursor = page.get("nextCursor").textValue();
        mvc.perform(get("/api/interviews/public").with(asOwner(OWNER))
                        .param("limit", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(R102))
                .andExpect(jsonPath("$.items[0].author").doesNotExist());
        mvc.perform(get("/api/interviews/public").with(asOwner(OWNER))
                        .param("city", "Shanghai").param("q", "Example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(R102));
        assertThat(first).doesNotContain("ownerId", "applicationId", "selfRating",
                "actionItems", "interviewerNotes", R103);
    }

    @Test
    void detailRecordsUniqueViewAndSanitizesQuestionContent() throws Exception {
        String detail = mvc.perform(get("/api/interviews/public/{id}", R102)
                        .with(asOwner(OTHER_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engagement.viewCount").value(1))
                .andExpect(jsonPath("$.questions[0].question").value("Question"))
                .andExpect(jsonPath("$.questions[0].originalAnswer").value("Original answer"))
                .andExpect(jsonPath("$.recentComments[0].content").value("Useful!"))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain("ownerId", "applicationId", "selfRating",
                "actionItems", "interviewerNotes", "Secret task");
        mvc.perform(get("/api/interviews/public/{id}", R102)
                        .with(asOwner(OTHER_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engagement.viewCount").value(1));
        mvc.perform(get("/api/interviews/public/{id}", R101)
                        .with(asOwner(OTHER_OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unavailableReviewDisappearsFromFeedAndDetailImmediately() throws Exception {
        jdbc.update("""
                update interview_reviews set visibility='private',published_at=null
                where id=?::uuid
                """, R102);
        mvc.perform(get("/api/interviews/public").with(asOwner(OTHER_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/interviews/public/{id}", R102)
                        .with(asOwner(OTHER_OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void hotAndLatestCursorsRemainStableWithDifferentOrderings() throws Exception {
        jdbc.update("update interview_reviews set like_count=10 where id=?::uuid", R102);
        mvc.perform(get("/api/interviews/public").with(asOwner(OWNER))
                        .param("sort", "latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(R201));
        String firstHot = mvc.perform(get("/api/interviews/public")
                        .with(asOwner(OWNER)).param("sort", "hot").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(R102))
                .andReturn().getResponse().getContentAsString();
        String cursor = mapper.readTree(firstHot).get("nextCursor").textValue();
        mvc.perform(get("/api/interviews/public").with(asOwner(OWNER))
                        .param("sort", "hot").param("limit", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(R201))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void emptyFilteredPageRetainsGlobalFacets() throws Exception {
        mvc.perform(get("/api/interviews/public").with(asOwner(OWNER))
                        .param("position", "Unknown Position"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.facets.cities.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void rejectsMalformedPublicListParameters() throws Exception {
        for (String parameter : new String[] {"limit=0", "limit=51", "sort=unknown",
                "limit=not-a-number", "cursor=bad", "q=" + "a".repeat(201),
                "city=" + "a".repeat(101), "position=" + "a".repeat(201)}) {
            String[] pair = parameter.split("=", 2);
            mvc.perform(get("/api/interviews/public").with(asOwner(OWNER))
                            .param(pair[0], pair[1]))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void criteriaAndCursorRejectEveryMalformedShape() {
        assertThatThrownBy(() -> new PublicInterviewCriteria(null, "", "", Sort.LATEST,
                null, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicInterviewCriteria("", null, "", Sort.LATEST,
                null, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicInterviewCriteria("", "", null, Sort.LATEST,
                null, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicInterviewCriteria("", "", "", null,
                null, 20)).isInstanceOf(IllegalArgumentException.class);
        var codec = new PublicInterviewCursorCodec(mapper);
        for (String json : new String[] {"[]", "{}", "{\"value\":42,\"id\":\"bad\"}",
                "{\"value\":\"2026-09-22T00:00:00Z\",\"id\":12}",
                "{\"value\":\"bad\",\"id\":\"" + R102 + "\"}",
                "{\"value\":\"bad|date\",\"id\":\"" + R102 + "\"}"}) {
            String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThatThrownBy(() -> codec.decode(encoded, Sort.LATEST))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        String hotWithoutSeparator = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"value\":\"42\",\"id\":\"" + R102 + "\"}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(() -> codec.decode(hotWithoutSeparator, Sort.HOT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

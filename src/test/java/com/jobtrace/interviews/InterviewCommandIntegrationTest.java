package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewCommandIntegrationTest extends InterviewReadDatabaseTest {

    @Autowired MockMvc mvc;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void installOwnerAwareFunctions() {
        jdbc.execute("""
                create or replace function create_interview_review_for_owner(
                  actor_id text,target_application_id uuid,target_stage_occurrence_id uuid,
                  stage_code recruitment_stage,interview_date date,payload jsonb)
                returns interview_reviews language plpgsql as $$
                declare app applications; occurrence application_stage_occurrences;
                  result interview_reviews; resolved_date date;
                begin
                  select * into app from applications
                    where id=target_application_id and owner_id=actor_id for update;
                  if not found then raise exception using errcode='P0002'; end if;
                  if target_stage_occurrence_id is not null then
                    select * into occurrence from application_stage_occurrences
                      where id=target_stage_occurrence_id and application_id=app.id;
                    if not found then raise exception using errcode='P0002'; end if;
                    resolved_date:=coalesce(interview_date,occurrence.occurred_on);
                  else
                    insert into application_stage_occurrences(id,application_id,stage,occurred_on,created_at)
                      values(gen_random_uuid(),app.id,stage_code,interview_date,now())
                      returning * into occurrence;
                    resolved_date:=interview_date;
                  end if;
                  insert into interview_reviews(id,owner_id,application_id,stage_occurrence_id,
                    stage_snapshot,interviewed_on,format,duration_minutes,interviewer_notes,
                    round_result,status,visibility,author_mode,version,created_at,updated_at)
                  values(gen_random_uuid(),actor_id,app.id,occurrence.id,occurrence.stage,
                    resolved_date,nullif(payload->>'format','')::interview_format,
                    nullif(payload->>'durationMinutes','')::integer,payload->>'interviewerNotes',
                    coalesce((payload->>'roundResult')::round_result,'pending'),
                    'draft','private','anonymous',1,now(),now()) returning * into result;
                  return result;
                end $$;
                create or replace function update_interview_review_for_owner(
                  actor_id text,target_id uuid,expected_version integer,payload jsonb)
                returns interview_reviews language plpgsql as $$
                declare result interview_reviews;
                begin
                  select * into result from interview_reviews
                    where id=target_id and owner_id=actor_id for update;
                  if not found then raise exception using errcode='P0002'; end if;
                  if result.version<>expected_version then
                    raise exception using errcode='40001';
                  end if;
                  update interview_reviews set
                    status=(payload->>'status')::review_status,
                    visibility=coalesce((payload->>'visibility')::interview_visibility,visibility),
                    highlights=payload->>'highlights',version=version+1,updated_at=now()
                    where id=target_id returning * into result;
                  delete from interview_questions where interview_review_id=target_id;
                  insert into interview_questions(id,interview_review_id,sort_order,category,question)
                  select gen_random_uuid(),target_id,ordinality-1,
                    (item->>'category')::question_category,item->>'question'
                  from jsonb_array_elements(payload->'questions')
                    with ordinality as value(item,ordinality);
                  return result;
                end $$;
                """);
    }

    @Test
    void createsEditsAndDeletesOwnedReview() throws Exception {
        String created = mvc.perform(post("/api/interviews").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":"%s","stage":"interview_2",
                                 "interviewedOn":"2026-09-19"}
                                """.formatted(APPLICATION_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).get("id").textValue();
        mvc.perform(patch("/api/interviews/{id}", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"version":1,"status":"completed","highlights":"Good discussion",
                                 "questions":[{"question":"Java concurrency","category":"technical"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.questions[0].question").value("Java concurrency"));
        mvc.perform(delete("/api/interviews/{id}", id).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from interview_reviews where id=?::uuid",
                Integer.class, id)).isZero();
    }

    @Test
    void rejectsStaleAndForeignWritesWithoutChangingRows() throws Exception {
        String update = """
                {"version":1,"status":"draft","questions":[]}
                """;
        mvc.perform(patch("/api/interviews/{id}", R201).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/interviews/{id}", R101).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update.replace("\"version\":1", "\"version\":2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
        mvc.perform(delete("/api/interviews/{id}", R201).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/interviews").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"applicationId":"%s","stage":"interview_2",
                                 "interviewedOn":"2026-09-19"}
                                """.formatted(OTHER_APPLICATION_ID)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select version from interview_reviews where id=?::uuid",
                Integer.class, R101)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from interview_reviews where id=?::uuid",
                Integer.class, R201)).isEqualTo(1);
    }
}

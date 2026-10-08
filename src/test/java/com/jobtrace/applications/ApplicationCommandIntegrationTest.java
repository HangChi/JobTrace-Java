package com.jobtrace.applications;

import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataAccessException;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationCommandIntegrationTest extends ApplicationReadDatabaseTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void installOwnerAwareFunctions() {
        jdbc.execute("""
                drop table if exists application_job_market_links,job_market_post_locations,
                  job_market_locations,job_market_source_records,job_market_sources,
                  job_market_posts,job_market_companies cascade
                """);
        jdbc.execute("""
                create or replace function create_application_for_owner(actor_id text, payload jsonb)
                returns applications language plpgsql as $$
                declare result applications;
                begin
                  insert into applications(id,owner_id,company_name,position_name,city,job_url,
                    applied_date,type,status,notes,latest_date,version,created_at,updated_at)
                  values(gen_random_uuid(),actor_id,payload->>'companyName',payload->>'positionName',
                    payload->>'city',payload->>'jobUrl',(payload->>'appliedDate')::date,
                    (payload->>'type')::application_type,(payload->>'status')::application_status,
                    payload->>'notes',(payload->>'appliedDate')::date,1,now(),now())
                  returning * into result;
                  insert into application_events(id,application_id,type,occurred_on,before,after,created_at)
                  values(gen_random_uuid(),result.id,'created',result.applied_date,null,
                    to_jsonb(result),now());
                  return result;
                end $$;
                create or replace function update_application_for_owner(
                  actor_id text,target_id uuid,expected_version integer,change_date date,payload jsonb)
                returns applications language plpgsql as $$
                declare result applications;
                begin
                  select * into result from applications where id=target_id and owner_id=actor_id for update;
                  if not found then raise exception using errcode='P0002',message='application_not_found'; end if;
                  if result.version<>expected_version then
                    raise exception using errcode='40001',message='application_version_conflict';
                  end if;
                  update applications set company_name=coalesce(payload->>'companyName',company_name),
                    position_name=coalesce(payload->>'positionName',position_name),
                    status=coalesce((payload->>'status')::application_status,status),
                    latest_date=greatest(latest_date,change_date),version=version+1,
                    updated_at=now() where id=target_id returning * into result;
                  insert into application_events(id,application_id,type,occurred_on,before,after,created_at)
                  values(gen_random_uuid(),result.id,'details_changed',change_date,null,
                    to_jsonb(result),now());
                  return result;
                end $$;
                """);
    }

    @Test
    void createsEditsAndChangesStatusWithLegacyShape() throws Exception {
        String created = mvc.perform(post("/api/applications").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":" New Company ","positionName":"Engineer",
                                 "appliedDate":"2026-09-20"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("New Company"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = new tools.jackson.databind.ObjectMapper().readTree(created).get("id").textValue();
        mvc.perform(patch("/api/applications/{id}", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Changed","positionName":"Engineer",
                                 "appliedDate":"2026-09-20","version":1,"changeDate":"2026-09-21"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Changed"))
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(patch("/api/applications/{id}/status", id)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"offer\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("offer"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void createdApplicationFlowsIntoOwnerReadModelButNotAnotherOwnersView() throws Exception {
        String created = mvc.perform(post("/api/applications").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Journey Company","positionName":"Engineer",
                                 "appliedDate":"2026-09-20"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = new tools.jackson.databind.ObjectMapper().readTree(created).get("id").textValue();

        mvc.perform(get("/api/applications/{id}", id).with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Journey Company"));
        mvc.perform(get("/api/applications").param("q", "Journey Company")
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get("/api/applications/{id}", id).with(asOwner(OTHER_OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void hidesForeignIdsAndRejectsStaleVersions() throws Exception {
        String update = """
                {"companyName":"Changed","positionName":"Engineer",
                 "appliedDate":"2026-09-01","version":3,"changeDate":"2026-09-20"}
                """;
        mvc.perform(patch("/api/applications/{id}", OTHER_ID)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/applications/{id}", FIRST_ID)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update.replace("\"version\":3", "\"version\":2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("conflict"));
        assertThat(jdbc.queryForObject("select version from applications where id=?::uuid",
                Integer.class, FIRST_ID)).isEqualTo(3);
    }

    @Test
    void deletesOnlyOwnedRecordsAndCountsDistinctBulkSelection() throws Exception {
        mvc.perform(delete("/api/applications/{id}", OTHER_ID)
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/applications").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + FIRST_ID + "\",\"" + FIRST_ID
                                + "\",\"" + OTHER_ID + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedCount").value(1));
        assertThat(jdbc.queryForObject("select count(*) from applications where id=?::uuid",
                Integer.class, OTHER_ID)).isEqualTo(1);
        mvc.perform(delete("/api/applications/{id}", SECOND_ID)
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsUnauthenticatedAndInvalidCreate() throws Exception {
        mvc.perform(post("/api/applications").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/applications").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void linkedJobMarketCreationUsesCanonicalPostAndRejectsDuplicates() throws Exception {
        jdbc.execute("""
                create table job_market_companies(id uuid primary key,canonical_name text not null);
                create table job_market_posts(id uuid primary key,company_id uuid not null,
                  title text not null,primary_apply_url text,status text not null);
                create table job_market_sources(id uuid primary key,is_official boolean not null);
                create table job_market_source_records(post_id uuid,source_id uuid,
                  external_job_id text,last_seen_at timestamptz);
                create table job_market_locations(id uuid primary key,display_name text not null);
                create table job_market_post_locations(post_id uuid,location_id uuid);
                create table application_job_market_links(application_id uuid,owner_id text,
                  post_id uuid,source_id uuid,external_job_id text,job_title_snapshot text,
                  company_name_snapshot text,location_snapshot text,apply_url_snapshot text,
                  unique(owner_id,post_id));
                insert into job_market_companies values
                  ('40000000-0000-4000-8000-000000000001','Canonical Company');
                insert into job_market_posts values
                  ('40000000-0000-4000-8000-000000000002',
                   '40000000-0000-4000-8000-000000000001','Canonical Role',
                   'https://example.test/apply','open');
                insert into job_market_sources values
                  ('40000000-0000-4000-8000-000000000003',true);
                insert into job_market_source_records values
                  ('40000000-0000-4000-8000-000000000002',
                   '40000000-0000-4000-8000-000000000003','external-1',now());
                insert into job_market_locations values
                  ('40000000-0000-4000-8000-000000000004','上海');
                insert into job_market_post_locations values
                  ('40000000-0000-4000-8000-000000000002',
                   '40000000-0000-4000-8000-000000000004');
                """);
        String body = """
                {"jobMarketPostId":"40000000-0000-4000-8000-000000000002",
                 "companyName":"Ignored","positionName":"Ignored","appliedDate":"2026-09-20"}
                """;
        mvc.perform(post("/api/applications").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Canonical Company"))
                .andExpect(jsonPath("$.positionName").value("Canonical Role"));
        mvc.perform(post("/api/applications").with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("job_market_application_exists"));
        assertThat(jdbc.queryForObject("select count(*) from application_job_market_links",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void storageFailureDoesNotPretendToSucceed() throws Exception {
        jdbc.execute("drop function update_application_for_owner(text,uuid,integer,date,jsonb)");
        mvc.perform(patch("/api/applications/{id}", FIRST_ID)
                        .with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Changed","positionName":"Engineer",
                                 "appliedDate":"2026-09-01","version":3,"changeDate":"2026-09-20"}
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void concurrentUpdatesWithOneVersionCommitOnlyOnce() throws Exception {
        int eventsBefore = jdbc.queryForObject("select count(*) from application_events "
                + "where application_id=?::uuid", Integer.class, FIRST_ID);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> updateAtSameVersion(start));
            var second = executor.submit(() -> updateAtSameVersion(start));
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("updated", "conflict");
        }
        assertThat(jdbc.queryForObject("select version from applications where id=?::uuid",
                Integer.class, FIRST_ID)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from application_events "
                + "where application_id=?::uuid", Integer.class, FIRST_ID))
                .isEqualTo(eventsBefore + 1);
    }

    private String updateAtSameVersion(CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            jdbc.queryForObject("""
                    select (update_application_for_owner(?, ?::uuid, ?, ?::date, ?::jsonb)).version
                    """, Integer.class, OWNER, FIRST_ID, 3, "2026-09-21",
                    "{\"companyName\":\"Concurrent\"}");
            return "updated";
        } catch (DataAccessException exception) {
            return "conflict";
        }
    }

    @Test
    void statusWriteMeetsLocalBudget() throws Exception {
        var samples = new ArrayList<Long>();
        for (int i = 0; i < 25; i++) {
            long started = System.nanoTime();
            mvc.perform(patch("/api/applications/{id}/status", FIRST_ID)
                            .with(asOwner(OWNER)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"submitted\",\"version\":" + (i + 3) + "}"))
                    .andExpect(status().isOk());
            if (i >= 5) {
                samples.add((System.nanoTime() - started) / 1_000_000);
            }
        }
        Collections.sort(samples);
        long p95 = samples.get(18);
        System.out.println("023 application status write p95 ms: " + p95
                + "; fixture applications=3");
        assertThat(p95).isLessThanOrEqualTo(1000);
    }
}

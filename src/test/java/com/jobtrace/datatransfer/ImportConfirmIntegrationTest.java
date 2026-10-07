package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.jobtrace.datatransfer.application.ImportStore;
import com.jobtrace.datatransfer.domain.ImportBatchException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ImportConfirmIntegrationTest extends ImportDatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ImportStore store;

    @Test
    void selectedValidRowCreatesEventAndInvalidRowSkipsOnce() throws Exception {
        String id = preview("公司,岗位,投递日期,阶段历史\n"
                + "Created Co,Engineer,2026-08-13,一面/AI面 + 2026-08-14\n"
                + ",Invalid,2026-08-13,\n", OWNER);
        mvc.perform(post("/api/imports/{id}/confirm", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[{"rowNumber":2,"action":"import"},
                                  {"rowNumber":3,"action":"import"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.failed").value(0));
        assertThat(jdbc.queryForObject("""
                select count(*) from applications where owner_id='owner-a'
                  and company_name='Created Co'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from application_events e join applications a
                  on a.id=e.application_id where a.company_name='Created Co'
                  and e.type='imported'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from application_stage_occurrences s join applications a
                  on a.id=s.application_id where a.company_name='Created Co'
                """, Integer.class)).isEqualTo(1);
        mvc.perform(post("/api/imports/{id}/confirm", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decisions\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("import_batch_conflict"));
    }

    @Test
    void foreignAndExpiredBatchesDoNotWriteAndMalformedDecisionsFail() throws Exception {
        String id = preview("公司,岗位,投递日期\nPrivate,Engineer,2026-08-13\n", OWNER);
        String command = "{\"decisions\":[{\"rowNumber\":2,\"action\":\"import\"}]}";
        mvc.perform(post("/api/imports/{id}/confirm", id).with(asOwner(OTHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/imports/{id}/confirm", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisions\":[{\"rowNumber\":-1,\"action\":\"import\"}]}"))
                .andExpect(status().isBadRequest());
        jdbc.update("update import_batches set expires_at=now()-interval '1 minute' where id=?::uuid", id);
        mvc.perform(post("/api/imports/{id}/confirm", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from applications where company_name='Private'",
                Integer.class)).isZero();
    }

    @Test
    void failedRowDoesNotRollBackOtherSelectedRows() throws Exception {
        String id = preview("公司,岗位,投递日期\nGood,Engineer,2026-08-13\n"
                + "Broken,Engineer,2026-08-13\n", OWNER);
        jdbc.update("""
                update import_rows set normalized_data=jsonb_set(normalized_data,
                  '{type}', '"invalid_type"') where batch_id=?::uuid and row_number=3
                """, id);
        mvc.perform(post("/api/imports/{id}/confirm", id).with(asOwner(OWNER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decisions":[{"rowNumber":2,"action":"import"},
                                  {"rowNumber":3,"action":"import"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.rows[1].error.code").value("import_row_failed"));
        assertThat(jdbc.queryForObject("select count(*) from applications where company_name='Good'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from applications where company_name='Broken'",
                Integer.class)).isZero();
    }

    @Test
    void concurrentConfirmationCreatesOnlyOneApplication() throws Exception {
        String id = preview("公司,岗位,投递日期\nConcurrent,Engineer,2026-08-13\n", OWNER);
        var decision = List.of(new ImportStore.Decision(2, "import"));
        var latch = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> confirmAfter(latch, UUID.fromString(id), decision));
            var second = executor.submit(() -> confirmAfter(latch, UUID.fromString(id), decision));
            latch.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("created", "conflict");
        }
        assertThat(jdbc.queryForObject("select count(*) from applications where company_name='Concurrent'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsMalformedDecisionShapeAndOutOfRangeRowNumber() throws Exception {
        String id = preview("公司,岗位,投递日期\nValidation,Engineer,2026-08-13\n", OWNER);
        for (String body : List.of("{}", "{\"decisions\":{}}",
                "{\"decisions\":[{\"rowNumber\":4294967297,\"action\":\"import\"}]}",
                "{\"decisions\":[{\"rowNumber\":2,\"action\":12}]}",
                "{\"decisions\":[{\"rowNumber\":2,\"action\":\"delete\"}]}")) {
            mvc.perform(post("/api/imports/{id}/confirm", id).with(asOwner(OWNER)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        assertThat(jdbc.queryForObject("select status::text from import_batches where id=?::uuid",
                String.class, id)).isEqualTo("previewed");
    }

    private String confirmAfter(CountDownLatch latch, UUID id, List<ImportStore.Decision> decision) {
        try {
            latch.await();
            store.confirm(OWNER, id, decision);
            return "created";
        } catch (ImportBatchException exception) {
            return "conflict";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private String preview(String csv, String owner) throws Exception {
        String body = mvc.perform(multipart("/api/imports/preview")
                        .file(ImportPreviewIntegrationTest.csv(csv))
                        .with(asOwner(owner)).with(csrf()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").textValue();
    }
}

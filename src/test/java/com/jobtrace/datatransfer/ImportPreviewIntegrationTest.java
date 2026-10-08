package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import com.jobtrace.datatransfer.infrastructure.ImportSpreadsheetReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ImportPreviewIntegrationTest extends ImportDatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void previewsMixedRowsAndOnlyOwnedDuplicatesWithoutApplicationWrites() throws Exception {
        int before = count("applications");
        var file = csv("公司,岗位,投递日期,类型,状态,阶段历史\n"
                + "Existing Co,Engineer,2026-08-13,秋招,已投递,一面/AI面 + 2026-08-14\n"
                + "Private Co,Engineer,2026-08-13,秋招,已投递,\n"
                + ",Invalid,2026-08-13,,,\n");
        mvc.perform(multipart("/api/imports/preview").file(file)
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(1))
                .andExpect(jsonPath("$.duplicateRows").value(1))
                .andExpect(jsonPath("$.rows[0].duplicateApplicationIds[0]")
                        .value("70000000-0000-4000-8000-000000000001"))
                .andExpect(jsonPath("$.rows[1].duplicateApplicationIds").isEmpty())
                .andExpect(jsonPath("$.rows[2].data").doesNotExist());
        assertThat(count("applications")).isEqualTo(before);
        assertThat(count("import_batches")).isEqualTo(1);
        assertThat(count("import_rows")).isEqualTo(3);
    }

    @Test
    void customMappingReplacesOldBatchOnlyAfterSuccessfulPreview() throws Exception {
        var file = csv("企业简称,职位名,日期\n映射公司,工程师,2026-08-13\n");
        String old = mvc.perform(multipart("/api/imports/preview").file(file)
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(0))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(old).get("id").textValue();
        mvc.perform(multipart("/api/imports/preview").file(file)
                        .param("mapping", "{\"企业简称\":\"companyName\","
                                + "\"职位名\":\"companyName\",\"日期\":\"appliedDate\"}")
                        .param("replaceBatchId", id).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(batchStatus(id)).isEqualTo("previewed");
        mvc.perform(multipart("/api/imports/preview").file(file)
                        .param("mapping", "{\"企业简称\":\"companyName\","
                                + "\"职位名\":\"positionName\",\"日期\":\"appliedDate\"}")
                        .param("replaceBatchId", id).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(1));
        assertThat(batchStatus(id)).isEqualTo("expired");
    }

    @Test
    void rejectsUnauthenticatedAndUnsupportedFiles() throws Exception {
        mvc.perform(multipart("/api/imports/preview").file(csv("公司,岗位,投递日期\nA,B,2026-08-13"))
                        .with(csrf())).andExpect(status().isUnauthorized());
        var wrong = new MockMultipartFile("file", "bad.exe", "application/octet-stream",
                "bad".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/imports/preview").file(wrong)
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("unsupported_format"));
        assertThat(count("import_batches")).isZero();
    }

    @Test
    void missingFileHasAValidationResponse() throws Exception {
        mvc.perform(multipart("/api/imports/preview").with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
    }

    @Test
    void preservesAllTenThousandRowsWithinTheDocumentedLimit() throws Exception {
        var csv = new StringBuilder("公司,岗位,投递日期\n");
        for (int index = 0; index < 10_000; index++) {
            csv.append("Company ").append(index).append(",Engineer,2026-08-13\n");
        }
        long started = System.nanoTime();
        mvc.perform(multipart("/api/imports/preview").file(csv(csv.toString()))
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(10_000))
                .andExpect(jsonPath("$.rows.length()").value(10_000));
        System.out.printf("026 local 10k-row preview ms: %d%n",
                (System.nanoTime() - started) / 1_000_000);
        assertThat(count("import_rows")).isEqualTo(10_000);
        assertThat(count("applications")).isEqualTo(2);
    }

    @Test
    void rejectsMalformedMappingAndOversizedPayload() throws Exception {
        var file = csv("公司,岗位,投递日期\nA,B,2026-08-13\n");
        for (String mapping : java.util.List.of("[]", "{bad", "{\"公司\":12}")) {
            mvc.perform(multipart("/api/imports/preview").file(file).param("mapping", mapping)
                            .with(asOwner(OWNER)).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        var large = new MockMultipartFile("file", "large.csv", "text/csv",
                new byte[ImportSpreadsheetReader.MAX_BYTES + 1]);
        mvc.perform(multipart("/api/imports/preview").file(large)
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("payload_too_large"));
        assertThat(count("import_batches")).isZero();
    }

    static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "applications.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String batchStatus(String id) {
        return jdbc.queryForObject("select status::text from import_batches where id=?::uuid",
                String.class, id);
    }
}

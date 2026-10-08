package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ImportRoundTripIntegrationTest extends ImportDatabaseTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void importedApplicationCanExportAsXlsxAndImportAgain() throws Exception {
        String first = preview(ImportPreviewIntegrationTest.csv("""
                公司,岗位,投递日期,类型,状态,阶段历史,备注
                Round Trip,Engineer,2026-08-13,暑期实习,已投递,一面/AI面 + 2026-08-14,复盘
                """));
        String confirmed = confirmPreview(first);
        String applicationId = mapper.readTree(confirmed).get("rows").get(0)
                .get("applicationId").textValue();
        byte[] workbook = mvc.perform(get("/api/exports/applications")
                        .param("scope", "selected").param("format", "xlsx")
                        .param("id", applicationId).with(asOwner(OWNER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(workbook).startsWith((byte) 0x50, (byte) 0x4b);
        var file = new MockMultipartFile("file", "round-trip.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);
        String second = preview(file);
        assertThat(mapper.readTree(second).get("columns").get("公司").textValue())
                .isEqualTo("companyName");
        assertThat(mapper.readTree(second).get("columns").get("ID").textValue())
                .isEmpty();
        String result = confirm(mapper.readTree(second).get("id").textValue());
        assertThat(mapper.readTree(result).get("created").intValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from applications where owner_id='owner-a'
                  and company_name='Round Trip' and type='summer_internship'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from application_stage_occurrences s join applications a
                  on a.id=s.application_id where a.company_name='Round Trip'
                  and s.stage='interview_1' and s.occurred_on='2026-08-14'
                """, Integer.class)).isEqualTo(2);
    }

    private String preview(MockMultipartFile file) throws Exception {
        return mvc.perform(multipart("/api/imports/preview").file(file)
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validRows").value(1))
                .andReturn().getResponse().getContentAsString();
    }

    private String confirmPreview(String preview) throws Exception {
        return confirm(mapper.readTree(preview).get("id").textValue());
    }

    private String confirm(String id) throws Exception {
        return mvc.perform(post("/api/imports/{id}/confirm", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decisions\":[{\"rowNumber\":2,\"action\":\"import\"}]}")
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}

package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationExportControllerTest extends ExportDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void csvDownloadHasOwnedContentAndPrivateHeaders() throws Exception {
        var response = mockMvc.perform(get("/api/exports/applications")
                        .param("format", "csv").param("scope", "all").with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("jobtrace-")))
                .andReturn().getResponse();
        String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("示例,科技", "第二公司").doesNotContain("不可见公司");
    }

    @Test
    void unknownFormatDefaultsToWorkbookAndEmptyScopeIsSafeNotFound() throws Exception {
        mockMvc.perform(get("/api/exports/applications")
                        .param("format", "unknown").with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
        var missing = mockMvc.perform(get("/api/exports/applications")
                        .param("format", "csv").param("q", "no-matching-company")
                        .with(asOwner(OWNER)))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        assertThat(missing.getContentAsString()).contains("not_found", "requestId")
                .doesNotContain(OWNER);
    }

    @Test
    void ordinaryPrincipalCannotChooseAnOwner() throws Exception {
        var ordinary = new UsernamePasswordAuthenticationToken(OWNER, null, java.util.List.of());
        mockMvc.perform(get("/api/exports/applications")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ordinary)))
                .andExpect(status().isUnauthorized());
    }
}

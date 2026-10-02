package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SelectedApplicationExportControllerTest extends ExportDatabaseTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void selectedDownloadIgnoresFiltersAndHasPrivateHeaders() throws Exception {
        var response = mockMvc.perform(get("/api/exports/applications")
                        .param("scope", "selected").param("format", "csv")
                        .param("id", A101, A201).param("q", "no-match")
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("jobtrace-selected-")))
                .andReturn().getResponse();
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .contains("示例,科技").doesNotContain("不可见公司");
    }

    @Test
    void invalidAndMissingSelectionsAreSafe() throws Exception {
        mockMvc.perform(get("/api/exports/applications").param("scope", "selected")
                        .with(asOwner(OWNER))).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/exports/applications").param("scope", "selected")
                        .param("id", A201).with(asOwner(OWNER)))
                .andExpect(status().isNotFound());
        var ordinary = new UsernamePasswordAuthenticationToken(OWNER, null, List.of());
        mockMvc.perform(get("/api/exports/applications").param("scope", "selected")
                        .param("id", A101)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ordinary)))
                .andExpect(status().isUnauthorized());
    }
}

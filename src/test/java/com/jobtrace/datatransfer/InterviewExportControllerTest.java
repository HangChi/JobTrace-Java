package com.jobtrace.datatransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewExportControllerTest extends ExportDatabaseTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void singleOwnedReviewDownloadsMarkdown() throws Exception {
        var response = mockMvc.perform(get("/api/exports/interviews")
                        .param("id", R301).with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().string("Content-Type", "text/markdown;charset=utf-8"))
                .andReturn().getResponse();
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8))
                .isEqualTo("# 一面复盘\n\n面经正文");
    }

    @Test
    void multipleOwnedReviewsZipAndMissingReviewNotFound() throws Exception {
        mockMvc.perform(get("/api/exports/interviews").param("id", R301, R302, R401)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"));
        mockMvc.perform(get("/api/exports/interviews").param("id", R401)
                        .with(asOwner(OWNER))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/exports/interviews").with(asOwner(OWNER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedSelectionAndOrdinaryPrincipalAreDenied() throws Exception {
        mockMvc.perform(get("/api/exports/interviews")
                        .param("id", Collections.nCopies(100, R301).toArray(String[]::new))
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown;charset=utf-8"));
        mockMvc.perform(get("/api/exports/interviews")
                        .param("id", Collections.nCopies(101, R301).toArray(String[]::new))
                        .with(asOwner(OWNER)))
                .andExpect(status().isBadRequest());
        var ordinary = new UsernamePasswordAuthenticationToken(OWNER, null, java.util.List.of());
        mockMvc.perform(get("/api/exports/interviews").param("id", R301)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ordinary)))
                .andExpect(status().isUnauthorized());
    }
}

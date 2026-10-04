package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.web.AccountSessionReadController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountSessionRevocationContractTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successfulResponseMatchesLegacyContractAndIsNotCacheable() throws Exception {
        mockMvc.perform(revoke("{\"id\":\"session-older\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.revoked").value(true));
        assertThat(exists("session-older")).isFalse();
    }

    @Test
    void absentRepeatedAndForeignIdsUseTheSamePrivateNotFoundProblem() throws Exception {
        mockMvc.perform(revoke("{\"id\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.detail").value("登录会话已不存在。"));
        mockMvc.perform(revoke("{\"id\":\"session-foreign\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.detail").value("登录会话已不存在。"));
        assertThat(exists("session-foreign")).isTrue();
    }

    @Test
    void rejectsMissingNullBlankNonStringOverlongUnknownAndMalformedInput() throws Exception {
        for (String body : new String[] {
                "{}", "{\"id\":null}", "{\"id\":\"\"}", "{\"id\":\" \"}",
                "{\"id\":42}", "{\"id\":true}", "{\"id\":{}}",
                "{\"id\":\"session-older\",\"extra\":true}", "{"}) {
            mockMvc.perform(revoke(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        mockMvc.perform(revoke("{\"id\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        assertThat(exists("session-older")).isTrue();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder revoke(
            String body) {
        return delete(AccountSessionReadController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(asOwner(OWNER))
                .with(csrf());
    }

    private boolean exists(String id) {
        return jdbc.queryForObject(
                "select exists(select 1 from sessions where id=?)", Boolean.class, id);
    }
}

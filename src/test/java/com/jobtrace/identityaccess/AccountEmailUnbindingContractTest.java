package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.web.AccountEmailUnbindingController;
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
class AccountEmailUnbindingContractTest extends AccountEmailUnbindingFixture {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successfulResponseAndNoStoreMatchLegacyShape() throws Exception {
        mockMvc.perform(request(CURRENT_SESSION, """
                        {"currentPassword":"Current-Password","ignored":true}
                        """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.emailVerified").value(false));
        assertThat(recoveryEmail(OWNER)).isNull();
        assertThat(consumedCodes(OWNER)).containsExactly(true, true);
    }

    @Test
    void wrongPasswordReportsStableFieldAndPreservesEmail() throws Exception {
        mockMvc.perform(request(CURRENT_SESSION, "{\"currentPassword\":\"wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("invalid_password"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(consumedCodes(OWNER)).containsExactly(false, false);
    }

    @Test
    void invalidBodyAndCurrentSessionAreSafe() throws Exception {
        for (String body : new String[] {"{}", "{\"currentPassword\":null}",
                "{\"currentPassword\":42}", "{\"currentPassword\":\"\"}",
                "{\"currentPassword\":\"" + "x".repeat(129) + "\"}", "{"}) {
            mockMvc.perform(request(CURRENT_SESSION, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        mockMvc.perform(request(FOREIGN_SESSION,
                        "{\"currentPassword\":\"Current-Password\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("email_unbind_failed"));
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(consumedCodes(OWNER)).containsExactly(false, false);
    }

    @Test
    void sixthAttemptReportsRateLimitWithoutChangingEmail() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(request(CURRENT_SESSION, "{\"currentPassword\":\"wrong\"}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(request(CURRENT_SESSION,
                        "{\"currentPassword\":\"Current-Password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("rate_limited"));
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(consumedCodes(OWNER)).containsExactly(false, false);
    }

    @Test
    void onlyDeleteIsExposed() throws Exception {
        mockMvc.perform(patch(AccountEmailUnbindingController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}").with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String sessionId, String body) {
        return delete(AccountEmailUnbindingController.PATH)
                .header(AccountSessionReadController.CURRENT_SESSION_HEADER, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(asOwner(OWNER)).with(csrf());
    }
}

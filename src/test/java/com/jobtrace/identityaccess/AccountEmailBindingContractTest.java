package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.web.AccountEmailBindingController;
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
class AccountEmailBindingContractTest extends AccountEmailBindingFixture {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void successReturnsNormalizedVerifiedAddressAndNoStore() throws Exception {
        mockMvc.perform(request(CURRENT_SESSION, """
                        {"email":"NEW@EXAMPLE.TEST","verificationCode":" 123456 ",
                         "currentPassword":"Current-Password","ignored":true}
                        """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.email").value(TARGET_EMAIL))
                .andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(codeConsumed()).isTrue();
    }

    @Test
    void invalidCodeHasEstablishedFieldErrorAndDurableAttempt() throws Exception {
        mockMvc.perform(request(CURRENT_SESSION, body(TARGET_EMAIL, "000000", CURRENT_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("invalid_email_code"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("verificationCode"));
        assertThat(codeAttempts()).isEqualTo(1);
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
    }

    @Test
    void wrongPasswordDoesNotConsumeCode() throws Exception {
        mockMvc.perform(request(CURRENT_SESSION, body(TARGET_EMAIL, CORRECT_CODE, "wrong")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_password"))
                .andExpect(jsonPath("$.detail").value("当前密码不正确。"));
        assertThat(codeAttempts()).isZero();
        assertThat(codeConsumed()).isFalse();
    }

    @Test
    void invalidFieldsAndForeignSessionNeverBind() throws Exception {
        for (String body : new String[] {"{}", "{\"email\":null}",
                "{\"email\":42}", body("invalid", CORRECT_CODE, CURRENT_PASSWORD),
                body(TARGET_EMAIL, "short", CURRENT_PASSWORD),
                body(TARGET_EMAIL, CORRECT_CODE, ""), "{"}) {
            mockMvc.perform(request(CURRENT_SESSION, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        mockMvc.perform(request(FOREIGN_SESSION,
                        body(TARGET_EMAIL, CORRECT_CODE, CURRENT_PASSWORD)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("email_bind_failed"));
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
        assertThat(codeConsumed()).isFalse();
    }

    @Test
    void conflictingAddressReportsSafeConflictAndPreservesCode() throws Exception {
        jdbc.update("update email_verification_codes set email=?,code_hash=? where id=?::uuid",
                FOREIGN_EMAIL, codeHasher.hash(OWNER, FOREIGN_EMAIL, CORRECT_CODE), CODE_ID);
        mockMvc.perform(request(CURRENT_SESSION,
                        body(FOREIGN_EMAIL, CORRECT_CODE, CURRENT_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_conflict"));
        assertThat(codeConsumed()).isFalse();
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String sessionId, String body) {
        return patch(AccountEmailBindingController.PATH)
                .header(AccountSessionReadController.CURRENT_SESSION_HEADER, sessionId)
                .contentType(MediaType.APPLICATION_JSON).content(body)
                .with(asOwner(OWNER)).with(csrf());
    }

    private static String body(String email, String code, String password) {
        return "{\"email\":\"" + email + "\",\"verificationCode\":\"" + code
                + "\",\"currentPassword\":\"" + password + "\"}";
    }
}

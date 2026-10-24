package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import com.jobtrace.identityaccess.web.RegistrationEmailCodeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test")
class RegistrationCodeContractTest extends AccountEmailBindingFixture {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountEmailCodeDelivery delivery;

    @Test
    void anonymousAcceptedResponseMatchesLegacyWithoutCode() throws Exception {
        mockMvc.perform(request("{\"email\":\"Fresh@Example.Test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.message").value("验证码已发送，10 分钟内有效。"))
                .andExpect(jsonPath("$.code").doesNotExist());
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email='fresh@example.test' and purpose='registration'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void invalidBodyAndBoundEmailHaveSafeErrors() throws Exception {
        for (String body : new String[] {"{}", "{\"email\":null}",
                "{\"email\":12}", "{\"email\":\"broken\"}", "{"}) {
            mockMvc.perform(request(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        mockMvc.perform(request("{\"email\":\"" + FOREIGN_EMAIL + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_conflict"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void rateLimitAndDeliveryFeedbackRemainSafe() throws Exception {
        mockMvc.perform(request("{\"email\":\"fresh@example.test\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(request("{\"email\":\"fresh@example.test\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
        doThrow(new AccountEmailCodeDeliveryException(false, true))
                .when(delivery).deliver(anyString(), anyString());
        mockMvc.perform(request("{\"email\":\"another@example.test\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("invalid_recipient"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String body) {
        return post(RegistrationEmailCodeController.PATH)
                .header("Origin", "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}

package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.domain.AccountEmailCodeDeliveryException;
import com.jobtrace.identityaccess.web.AccountEmailCodeController;
import com.jobtrace.identityaccess.web.AccountSessionReadController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AccountEmailCodeContractTest extends AccountEmailBindingFixture {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountEmailCodeDelivery delivery;

    @Test
    void acceptedResponseHasNoCodeAndNoStore() throws Exception {
        mockMvc.perform(request(CURRENT_SESSION, "{\"email\":\"Fresh@Example.Test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.message").value("验证码已发送，10 分钟内有效。"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void invalidAddressAndForeignSessionHaveSafeResponses() throws Exception {
        for (String body : new String[] {"{}", "{\"email\":null}",
                "{\"email\":12}", "{\"email\":\"bad\"}", "{"}) {
            mockMvc.perform(request(CURRENT_SESSION, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        mockMvc.perform(request(FOREIGN_SESSION, "{\"email\":\"fresh@example.test\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("email_code_failed"));
    }

    @Test
    void conflictRateLimitAndDeliveryErrorsStaySafe() throws Exception {
        mockMvc.perform(request(CURRENT_SESSION, "{\"email\":\""
                + FOREIGN_EMAIL + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_conflict"));
        mockMvc.perform(request(CURRENT_SESSION,
                "{\"email\":\"fresh@example.test\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(request(CURRENT_SESSION,
                "{\"email\":\"fresh@example.test\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
        doThrow(new AccountEmailCodeDeliveryException(false, true))
                .when(delivery).deliver(anyString(), anyString());
        mockMvc.perform(request(CURRENT_SESSION,
                "{\"email\":\"another@example.test\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("invalid_recipient"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
        assertThat(recoveryEmail(OWNER)).isEqualTo(OWNER_EMAIL);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String session, String body) {
        return post(AccountEmailCodeController.PATH)
                .header(AccountSessionReadController.CURRENT_SESSION_HEADER, session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(asOwner(OWNER)).with(csrf());
    }
}

package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.RegisterAccount;
import com.jobtrace.identityaccess.application.RegistrationPreflight;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException.Reason;
import com.jobtrace.identityaccess.web.RegistrationController;
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
class RegistrationSecurityTest extends AccountEmailBindingFixture {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterAccount registration;

    @MockitoBean
    private RegistrationPreflight preflight;

    @Test
    void exactAnonymousPostAllowsMissingOriginAndNoCsrfToken() throws Exception {
        mockMvc.perform(base()).andExpect(status().isAccepted());
        mockMvc.perform(post("/api/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void explicitlyCrossSiteAndMismatchedOriginCannotReachPreflight() throws Exception {
        mockMvc.perform(base().header("Origin", "https://jobtrace.test")
                        .header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_rejected"));
        mockMvc.perform(base().header("Origin", "https://evil.test"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_rejected"));
        verify(preflight, never()).check(any());
        verify(registration, never()).execute(any());
    }

    @Test
    void rateAndChallengeFailuresAreNonCacheable() throws Exception {
        doThrow(new AccountEmailCodeRateLimitedException()).when(preflight).check(any());
        mockMvc.perform(base().header("Origin", "https://jobtrace.test")
                        .header("x-auth-challenge", "token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("rate_limited"));

        org.mockito.Mockito.reset(preflight);
        doThrow(new RegistrationChallengeException(Reason.FAILED))
                .when(preflight).check("bad-token");
        mockMvc.perform(base().header("Origin", "https://jobtrace.test")
                        .header("x-auth-challenge", "bad-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("challenge_failed"));
        verify(registration, never()).execute(any());
    }

    @Test
    void missingAndUnavailableConfiguredChallengeHaveStableCodes() throws Exception {
        doThrow(new RegistrationChallengeException(Reason.REQUIRED))
                .when(preflight).check(null);
        mockMvc.perform(base().header("Origin", "https://jobtrace.test"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("challenge_required"));
        org.mockito.Mockito.reset(preflight);
        doThrow(new RegistrationChallengeException(Reason.UNAVAILABLE))
                .when(preflight).check(null);
        mockMvc.perform(base().header("Origin", "https://jobtrace.test"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("challenge_unavailable"));
    }

    @Test
    void responseDoesNotExposePasswordOrCode() throws Exception {
        var response = mockMvc.perform(base().header("Origin", "https://jobtrace.test"))
                .andExpect(status().isAccepted()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("Password123!", "123456");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder base() {
        return post(RegistrationController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"new_user","email":"new@example.test",
                        "verificationCode":"123456","password":"Password123!"}
                        """);
    }
}

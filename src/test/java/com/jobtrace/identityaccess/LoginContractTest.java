package com.jobtrace.identityaccess;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.LoginAccount;
import com.jobtrace.identityaccess.application.LoginPreflight;
import com.jobtrace.identityaccess.domain.AccountEmailCodeRateLimitedException;
import com.jobtrace.identityaccess.domain.InvalidCredentialsException;
import com.jobtrace.identityaccess.domain.LoginUnavailableException;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException;
import com.jobtrace.identityaccess.domain.RegistrationChallengeException.Reason;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "BETTER_AUTH_SECRET=0123456789abcdef0123456789abcdef",
        "BETTER_AUTH_URL=https://jobtrace.test",
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test"})
class LoginContractTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private LoginAccount login;
    @MockitoBean private LoginPreflight preflight;

    @Test
    void usernameAliasReturnsOnlyDestinationAndCookie() throws Exception {
        when(login.execute(any())).thenReturn(new LoginAccount.Result("/admin", "opaque-token"));
        mockMvc.perform(request("{\"username\":\"owner\",\"password\":\"pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirectTarget").value("/admin"))
                .andExpect(jsonPath("$.sessionToken").doesNotExist())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
    }

    @Test
    void missingOrMalformedFieldsAreValidationErrors() throws Exception {
        for (String body : new String[] {"{", "{}", "null", "{\"identifier\":42}",
                "{\"identifier\":null,\"username\":\"owner\",\"password\":\"pass\"}",
                "{\"username\":\"owner\",\"password\":null}"}) {
            mockMvc.perform(request(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("validation"))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    @Test
    void preflightFailuresMapToStableCodes() throws Exception {
        doThrow(new AccountEmailCodeRateLimitedException()).when(preflight).check(any());
        mockMvc.perform(request(valid())).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
        mockMvc.perform(request("{"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("rate_limited"));
        reset(preflight);
        for (Reason reason : Reason.values()) {
            doThrow(new RegistrationChallengeException(reason)).when(preflight).check(any());
            String code = switch (reason) {
                case REQUIRED -> "challenge_required";
                case FAILED -> "challenge_failed";
                case UNAVAILABLE -> "challenge_unavailable";
            };
            mockMvc.perform(request(valid())).andExpect(jsonPath("$.code").value(code))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
            reset(preflight);
        }
    }

    @Test
    void credentialAndDependencyFailuresNeverExposePrivateState() throws Exception {
        when(login.execute(any())).thenThrow(new InvalidCredentialsException());
        mockMvc.perform(request(valid())).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
        reset(login);
        when(login.execute(any())).thenThrow(new LoginUnavailableException());
        mockMvc.perform(request(valid())).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("login_unavailable"));
        reset(login);
        when(login.execute(any())).thenThrow(new DataAccessResourceFailureException("private database"));
        String body = mockMvc.perform(request(valid())).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("private database");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String body) {
        return post("/api/auth/login").header("Origin", "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String valid() {
        return "{\"identifier\":\"owner\",\"password\":\"pass\"}";
    }
}

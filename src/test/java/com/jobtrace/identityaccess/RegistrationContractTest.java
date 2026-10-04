package com.jobtrace.identityaccess;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.RegisterAccount;
import com.jobtrace.identityaccess.application.RegistrationPreflight;
import com.jobtrace.identityaccess.domain.InvalidRegistrationCodeException;
import com.jobtrace.identityaccess.domain.RegistrationConflictException;
import com.jobtrace.identityaccess.domain.AccountEmailBindingUnavailableException;
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
import org.springframework.dao.DataAccessResourceFailureException;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test")
class RegistrationContractTest extends AccountEmailBindingFixture {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegisterAccount registration;

    @MockitoBean
    private RegistrationPreflight preflight;

    @Test
    void anonymousAcceptedResponseDoesNotIssueSession() throws Exception {
        mockMvc.perform(request(validBody()))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.message").value("账号已创建，请登录。"))
                .andExpect(jsonPath("$.token").doesNotExist());
        verify(registration).execute(any(RegisterAccount.Input.class));
    }

    @Test
    void malformedAndNonStringFieldsAreValidationErrors() throws Exception {
        for (String body : new String[] {"{", "{}", "null", "{\"username\":42}",
                "{\"username\":\"name\",\"email\":null}"}) {
            mockMvc.perform(request(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
    }

    @Test
    void codeAndUniquenessFailuresHaveStableFieldErrors() throws Exception {
        doThrow(new InvalidRegistrationCodeException()).when(registration).execute(any());
        mockMvc.perform(request(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_email_code"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("verificationCode"));

        org.mockito.Mockito.reset(registration);
        doThrow(new RegistrationConflictException("username"))
                .when(registration).execute(any());
        mockMvc.perform(request(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("registration_conflict"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("username"));

        org.mockito.Mockito.reset(registration);
        doThrow(new RegistrationConflictException("email"))
                .when(registration).execute(any());
        mockMvc.perform(request(validBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void unavailableSigningAndDatabaseDependenciesRemainSafe() throws Exception {
        doThrow(new AccountEmailBindingUnavailableException())
                .when(registration).execute(any());
        mockMvc.perform(request(validBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("email_verification_unavailable"));
        org.mockito.Mockito.reset(registration);
        doThrow(new DataAccessResourceFailureException("private database detail"))
                .when(registration).execute(any());
        var response = mockMvc.perform(request(validBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("registration_unavailable"))
                .andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(response.getContentAsString())
                .doesNotContain("private database detail");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String body) {
        return post(RegistrationController.PATH)
                .header("Origin", "https://jobtrace.test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String validBody() {
        return """
                {"username":"new_user","email":"new@example.test",
                "verificationCode":"123456","password":"Password123!"}
                """;
    }
}

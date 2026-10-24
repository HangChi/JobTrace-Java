package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.AccountEmailCodeDelivery;
import com.jobtrace.identityaccess.web.RegistrationCodeOriginGuard;
import com.jobtrace.identityaccess.web.RegistrationEmailCodeController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties =
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test")
class RegistrationCodeSecurityTest extends AccountEmailBindingFixture {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountEmailCodeDelivery delivery;

    @Test
    void exactAnonymousPostWorksWithoutSessionOrCsrfToken() throws Exception {
        mockMvc.perform(base().header("Origin", "https://jobtrace.test"))
                .andExpect(status().isAccepted());
    }

    @Test
    void crossSiteAndMismatchedOriginCannotIssue() throws Exception {
        mockMvc.perform(base().header("Origin", "https://jobtrace.test")
                        .header("Sec-Fetch-Site", "cross-site"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_rejected"));
        mockMvc.perform(base().header("Origin", "https://evil.test"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_rejected"));
        assertThat(jdbc.queryForObject("""
                select count(*) from email_verification_codes
                where email='fresh@example.test' and purpose='registration'
                """, Integer.class)).isZero();
    }

    @Test
    void legacyMissingOriginIsAllowedButPublicPermissionIsExact() throws Exception {
        mockMvc.perform(base()).andExpect(status().isAccepted());
        mockMvc.perform(post("/api/profile/email/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"other@example.test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void originGuardHandlesConfiguredUrlAndInvalidConfiguration() {
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "https://jobtrace.test");
        new RegistrationCodeOriginGuard("https://jobtrace.test/auth").assertAllowed(request);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new RegistrationCodeOriginGuard("").assertAllowed(request))
                .isInstanceOf(com.jobtrace.identityaccess.domain.RegistrationCodeOriginException.class);
        for (String invalid : new String[] {"not a URL", "file:///tmp/auth"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> new RegistrationCodeOriginGuard(invalid).assertAllowed(request))
                    .isInstanceOf(
                            com.jobtrace.identityaccess.domain.RegistrationCodeOriginException.class);
        }
        var local = new MockHttpServletRequest();
        local.addHeader("Origin", "http://localhost:3000");
        new RegistrationCodeOriginGuard("http://localhost:3000/sign-in")
                .assertAllowed(local);
        var standardPort = new MockHttpServletRequest();
        standardPort.addHeader("Origin", "https://jobtrace.test");
        new RegistrationCodeOriginGuard("https://JOBTRACE.TEST:443/sign-in")
                .assertAllowed(standardPort);
        local.addHeader("Sec-Fetch-Site", "cross-site");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new RegistrationCodeOriginGuard("http://localhost:3000")
                        .assertAllowed(local))
                .isInstanceOf(com.jobtrace.identityaccess.domain.RegistrationCodeOriginException.class);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder base() {
        return post(RegistrationEmailCodeController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"fresh@example.test\"}");
    }
}

package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.LogoutSession;
import com.jobtrace.identityaccess.infrastructure.BetterAuthSessionCookie;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "BETTER_AUTH_SECRET=0123456789abcdef0123456789abcdef",
        "BETTER_AUTH_URL=https://jobtrace.test",
        "jobtrace.registration-code.allowed-origin=https://jobtrace.test"})
class LogoutFailureContractTest extends AccountEmailBindingFixture {
    @Autowired private MockMvc mockMvc;
    @Autowired private BetterAuthSessionCookie cookie;
    @Autowired private MeterRegistry registry;
    @MockitoBean private LogoutSession logout;

    @Test
    void databaseFailureStillClearsBrowserCookieWithBoundedDiagnostic() throws Exception {
        int before = jdbc.queryForObject("select count(*) from sessions", Integer.class);
        doThrow(new DataAccessResourceFailureException("private token and database detail"))
                .when(logout).execute(anyString());
        String signed = cookie.header("token-" + CURRENT_SESSION).split(";", 2)[0];
        var response = mockMvc.perform(post("/api/auth/logout")
                .header("Origin", "https://jobtrace.test").header("Cookie", signed))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse();
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(before);
        assertThat(registry.counter("jobtrace.identity_access.logout.requests",
                "outcome", "delete_failure").count()).isGreaterThan(0);
    }
}

package com.jobtrace.identityaccess;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.RequestPasswordReset;
import com.jobtrace.identityaccess.application.ResetPassword;
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
class PasswordRecoveryControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RequestPasswordReset requestReset;
    @MockitoBean private ResetPassword reset;

    @Test
    void requestResponseIsGenericAndNoStore() throws Exception {
        for (String email : new String[]{"owner@example.test", "missing@example.test"}) {
            mockMvc.perform(post("/api/auth/request-password-reset")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.message")
                            .value("如果该邮箱存在，我们已发送密码重置说明。"));
            verify(requestReset).execute(email, "127.0.0.1");
        }
    }

    @Test
    void callbackNeverRedirectsToAnUntrustedHost() throws Exception {
        String token = "abcdefghijklmnopqrstuvwx";
        when(reset.valid(token)).thenReturn(true);
        mockMvc.perform(get("/api/auth/reset-password/" + token)
                .param("callbackURL", "https://outside.test"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/auth/reset-password/" + token)
                .param("callbackURL", "/reset-password"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "/reset-password?token=" + token));
    }

    @Test
    void consumeRequiresSameOriginAndReturnsNonCacheableSuccess() throws Exception {
        String body = "{\"token\":\"abcdefghijklmnopqrstuvwx\","
                + "\"newPassword\":\"New-Password\"}";
        mockMvc.perform(post("/api/auth/reset-password")
                .header(HttpHeaders.ORIGIN, "https://outside.test")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.status").value(true));
        verify(reset).execute(anyString(), anyString());
    }
}

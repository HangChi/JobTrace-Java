package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.application.PasswordHasher;
import com.jobtrace.identityaccess.web.AccountPasswordController;
import com.jobtrace.identityaccess.web.AccountSessionReadController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;

@SpringBootTest
@AutoConfigureMockMvc
class AccountPasswordContractTest extends AccountPasswordDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordHasher hasher;

    @Test
    void responseMatchesEstablishedMessageAndUnknownFieldsAreIgnored() throws Exception {
        mockMvc.perform(change(CURRENT_SESSION, """
                        {"currentPassword":"Current-Password",
                         "newPassword":"New-Password","ignored":"value"}
                        """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.message")
                        .value("密码已更新，其他设备已退出登录。"));

        assertThat(hasher.matches("New-Password", ownerHash())).isTrue();
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION);
    }

    @Test
    void wrongPasswordReturnsFieldErrorAndMutatesNothing() throws Exception {
        mockMvc.perform(change(CURRENT_SESSION, """
                        {"currentPassword":"Definitely-Wrong-Password",
                         "newPassword":"12345678"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.code").value("invalid_password"))
                .andExpect(jsonPath("$.detail").value("当前密码不正确，请重新输入。"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

    @Test
    void invalidBodiesIdentifyFieldsAndMutateNothing() throws Exception {
        for (String body : new String[] {
                "{}", "{\"currentPassword\":null,\"newPassword\":\"12345678\"}",
                "{\"currentPassword\":42,\"newPassword\":\"12345678\"}",
                "{\"currentPassword\":\"\",\"newPassword\":\"12345678\"}",
                "{\"currentPassword\":\"Current-Password\",\"newPassword\":null}",
                "{\"currentPassword\":\"Current-Password\",\"newPassword\":\"short\"}",
                "{\"currentPassword\":\"Current-Password\",\"newPassword\":\""
                        + "x".repeat(17) + "\"}",
                "{"}) {
            mockMvc.perform(change(CURRENT_SESSION, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

    @Test
    void missingOrForeignCurrentSessionFailsSafely() throws Exception {
        for (String session : new String[] {"missing", FOREIGN_SESSION}) {
            mockMvc.perform(change(session, """
                            {"currentPassword":"Current-Password",
                             "newPassword":"New-Password"}
                            """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("password_change_failed"));
        }
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

    @Test
    void missingSessionHeaderAndCredentialFailWithoutMutation() throws Exception {
        mockMvc.perform(post(AccountPasswordController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Current-Password\","
                                + "\"newPassword\":\"New-Password\"}")
                        .with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation"));
        jdbc.update("delete from accounts where user_id=?", OWNER);
        mockMvc.perform(change(CURRENT_SESSION, """
                        {"currentPassword":"Current-Password",
                         "newPassword":"New-Password"}
                        """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("password_change_failed"));
        assertThat(ownerSessions()).containsExactly(CURRENT_SESSION, OTHER_SESSION);
    }

    @Test
    void surfaceExposesOnlyPostAndUnsupportedMethodsDoNotMutate() throws Exception {
        assertThat(Arrays.stream(AccountPasswordController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class))).hasSize(1);
        for (var request : java.util.List.of(
                get(AccountPasswordController.PATH), patch(AccountPasswordController.PATH),
                delete(AccountPasswordController.PATH))) {
            mockMvc.perform(request.with(asOwner(OWNER)).with(csrf()))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertThat(ownerHash()).isEqualTo(CURRENT_HASH);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder change(
            String sessionId, String body) {
        return post(AccountPasswordController.PATH)
                .header(AccountSessionReadController.CURRENT_SESSION_HEADER, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(asOwner(OWNER))
                .with(csrf());
    }
}

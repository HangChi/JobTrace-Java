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

import com.jobtrace.identityaccess.web.AccountProfileController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PatchMapping;

@SpringBootTest
@AutoConfigureMockMvc
class AccountProfileContractTest extends AccountProfileDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void responseMatchesEstablishedNormalizedProfileAndIgnoresUnknownFields() throws Exception {
        mockMvc.perform(update(OWNER, """
                        {"displayName":"  New Name  ",
                         "image":"https://img.test/new.png","ignored":"value"}
                        """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.id").value(OWNER))
                .andExpect(jsonPath("$.displayName").value("New Name"))
                .andExpect(jsonPath("$.image").value("https://img.test/new.png"))
                .andExpect(jsonPath("$.recoveryEmail").value("owner@example.test"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void omittedNullAndEmptyImageAllClearAvatar() throws Exception {
        for (String body : new String[] {
                "{\"displayName\":\"Name\"}",
                "{\"displayName\":\"Name\",\"image\":null}",
                "{\"displayName\":\"Name\",\"image\":\"\"}"}) {
            jdbc.update("update users set image='https://img.test/reset.png' where id=?", OWNER);
            mockMvc.perform(update(OWNER, body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.image").doesNotExist());
            assertThat(jdbc.queryForObject(
                    "select image is null from users where id=?", Boolean.class, OWNER)).isTrue();
        }
    }

    @Test
    void invalidBodiesAndMissingOwnerAreSafeAndDoNotMutateProfiles() throws Exception {
        for (String body : new String[] {
                "{}", "{\"displayName\":null}", "{\"displayName\":42}",
                "{\"displayName\":\"\"}", "{\"displayName\":\" \"}",
                "{\"displayName\":\"" + "x".repeat(101) + "\"}",
                "{\"displayName\":\"Name\",\"image\":42}",
                "{\"displayName\":\"Name\",\"image\":\"relative.png\"}", "{"}) {
            mockMvc.perform(update(OWNER, body))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                    .andExpect(jsonPath("$.code").value("validation"));
        }
        mockMvc.perform(update("missing", "{\"displayName\":\"Name\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"))
                .andExpect(jsonPath("$.detail").value("没有找到账号资料。"));
        assertThat(jdbc.queryForObject(
                "select display_name from users where id=?", String.class, OWNER))
                .isEqualTo("Old Name");
    }

    @Test
    void surfaceExposesReadAndPatchWhileUnsupportedMethodsDoNotMutate() throws Exception {
        assertThat(Arrays.stream(AccountProfileController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PatchMapping.class))).hasSize(1);
        mockMvc.perform(get(AccountProfileController.PATH).with(asOwner(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(OWNER))
                .andExpect(jsonPath("$.displayName").value("Old Name"));
        for (var request : java.util.List.of(
                post(AccountProfileController.PATH), delete(AccountProfileController.PATH))) {
            mockMvc.perform(request.with(asOwner(OWNER)).with(csrf()))
                    .andExpect(status().isMethodNotAllowed());
        }
        assertThat(jdbc.queryForObject(
                "select display_name from users where id=?", String.class, OWNER))
                .isEqualTo("Old Name");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder update(
            String owner, String body) {
        return patch(AccountProfileController.PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(asOwner(owner))
                .with(csrf());
    }
}

package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.identityaccess.web.AccountSessionReadController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootTest
@AutoConfigureMockMvc
class AccountSessionReadOnlySurfaceTest extends AccountSessionReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void controllerMapsOnlyGetAndUnsupportedMutationsDoNotChangeSessions() throws Exception {
        assertThat(Arrays.stream(AccountSessionReadController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))).hasSize(1);
        int before = jdbc.queryForObject("select count(*) from sessions", Integer.class);
        mockMvc.perform(get(AccountSessionReadController.PATH)
                        .header(AccountSessionReadController.CURRENT_SESSION_HEADER, CURRENT)
                        .with(asOwner(OWNER)))
                .andExpect(status().isOk());
        mockMvc.perform(delete(AccountSessionReadController.PATH)
                        .with(asOwner(OWNER))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch(AccountSessionReadController.PATH)
                        .with(asOwner(OWNER))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
        assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class))
                .isEqualTo(before);
    }
}

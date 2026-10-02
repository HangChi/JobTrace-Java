package com.jobtrace.applications;

import static com.jobtrace.applications.ApplicationReadDatabaseTest.asOwner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.applications.web.ApplicationReadController;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationReadOnlySurfaceTest extends ApplicationReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void controllerDeclaresOnlyTwoGetHandlersAndNoMutationIsMapped() throws Exception {
        assertThat(Arrays.stream(ApplicationReadController.class.getDeclaredMethods())
                        .filter(method -> method.isAnnotationPresent(GetMapping.class)))
                .hasSize(2);
        mockMvc.perform(post("/api/applications").with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/applications/{id}", FIRST_ID).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/applications/{id}", FIRST_ID).with(asOwner(OWNER)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        assertThat(jdbc.queryForObject(
                "select to_regclass('public.flyway_schema_history')::text", String.class)).isNull();
    }
}

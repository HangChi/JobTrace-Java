package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobtrace.applicationdialog.web.ApplicationDialogController;
import com.jobtrace.interviews.web.InterviewReadController;
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
class InterviewReadOnlySurfaceTest extends InterviewReadDatabaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void onlyThreePrivateGetHandlersExistAndNoMutationIsMapped() throws Exception {
        assertThat(Arrays.stream(InterviewReadController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class)))
                .hasSize(2);
        assertThat(Arrays.stream(InterviewReadController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value())))
                .containsExactly("/{id}");
        assertThat(Arrays.stream(ApplicationDialogController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .flatMap(method -> Arrays.stream(method.getAnnotation(GetMapping.class).value())))
                .containsExactly("/api/applications/{id}/detail");
        mockMvc.perform(post("/api/interviews").with(asOwner(OWNER))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/api/interviews/{id}", R102).with(asOwner(OWNER))
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/applications/{id}/detail", APPLICATION_ID)
                        .with(asOwner(OWNER)).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}

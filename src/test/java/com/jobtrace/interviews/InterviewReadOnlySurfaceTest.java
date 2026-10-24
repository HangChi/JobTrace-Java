package com.jobtrace.interviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void privateReadsRemainSeparatedFromCommands() throws Exception {
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
        assertThat(Arrays.stream(com.jobtrace.interviews.web.InterviewCommandController.class
                .getDeclaredMethods()).filter(method -> method.isAnnotationPresent(
                        org.springframework.web.bind.annotation.PostMapping.class))).hasSize(1);
        assertThat(Arrays.stream(com.jobtrace.interviews.web.InterviewCommandController.class
                .getDeclaredMethods()).filter(method -> method.isAnnotationPresent(
                        org.springframework.web.bind.annotation.PatchMapping.class))).hasSize(1);
        mockMvc.perform(delete("/api/applications/{id}/detail", APPLICATION_ID)
                        .with(asOwner(OWNER)).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isMethodNotAllowed());
    }
}

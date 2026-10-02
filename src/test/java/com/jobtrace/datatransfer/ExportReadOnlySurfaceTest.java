package com.jobtrace.datatransfer;

import com.jobtrace.datatransfer.web.ExportController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@SpringBootTest
@AutoConfigureMockMvc
class ExportReadOnlySurfaceTest extends ExportDatabaseTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void bothDownloadsLeaveSourceTablesUnchanged() throws Exception {
        long applicationsBefore = count("applications");
        long reviewsBefore = count("interview_reviews");
        long questionsBefore = count("interview_questions");
        mockMvc.perform(get("/api/exports/applications").param("format", "csv")
                        .with(asOwner(OWNER))).andExpect(status().isOk());
        mockMvc.perform(get("/api/exports/interviews").param("id", R301, R302)
                        .with(asOwner(OWNER))).andExpect(status().isOk());
        assertThat(count("applications")).isEqualTo(applicationsBefore);
        assertThat(count("interview_reviews")).isEqualTo(reviewsBefore);
        assertThat(count("interview_questions")).isEqualTo(questionsBefore);
        assertThat(Files.exists(Path.of("src/main/resources/db/migration"))).isFalse();
    }

    @Test
    void exportSurfaceContainsOnlyTwoGetHandlersAndSelectStatements() throws Exception {
        var methods = Arrays.asList(ExportController.class.getDeclaredMethods());
        assertThat(methods.stream().filter(method -> method.isAnnotationPresent(GetMapping.class)))
                .hasSize(2);
        assertThat(methods.stream().filter(method -> method.isAnnotationPresent(PostMapping.class)))
                .isEmpty();
        String adapter = Files.readString(Path.of(
                "src/main/java/com/jobtrace/datatransfer/infrastructure/PostgresExportReadQuery.java"));
        assertThat(adapter).doesNotContain("jdbc.update(", "jdbc.execute(", "insert into ",
                "delete from ", "update applications ", "update interview_reviews ");
        assertThat(Files.exists(Path.of("src/main/resources/db/migration"))).isFalse();
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}

package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminReadOnlySurfaceTest {

    @Test
    void productionSurfaceContainsNoMutationOrMigration() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/jobtrace/admin/web/AdminReadController.java"));
        String repository = Files.readString(Path.of(
                "src/main/java/com/jobtrace/admin/infrastructure/PostgresAdminReadQuery.java"))
                .toLowerCase();

        assertThat(controller).contains("@GetMapping")
                .doesNotContain("@PostMapping", "@PutMapping", "@DeleteMapping");
        assertThat(repository).doesNotContain(" insert into ", " update ", " delete from ",
                "change_user_access_as", " for update", "refresh materialized");
        assertThat(Files.exists(Path.of("src/main/resources/db/migration"))).isFalse();
    }
}

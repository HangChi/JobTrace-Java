package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminAccessSurfaceTest {

    @Test
    void commandSurfaceIsOnePatchAndOneExistingPolicyCall() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/jobtrace/admin/web/AdminReadController.java"));
        String adapter = Files.readString(Path.of(
                "src/main/java/com/jobtrace/admin/infrastructure/PostgresAdminAccessCommand.java"));

        assertThat(controller).doesNotContain("@PostMapping", "@PutMapping", "@DeleteMapping");
        assertThat(occurrences(controller, "@PatchMapping(\"/users/{id}\")")).isOne();
        assertThat(adapter).doesNotContain("update users", "delete from sessions", "insert into");
        assertThat(occurrences(adapter, "change_user_access_as")).isOne();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration"))).isFalse();
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}

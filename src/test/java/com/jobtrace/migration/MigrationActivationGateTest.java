package com.jobtrace.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MigrationActivationGateTest {
    @Test
    void unapprovedLegacyBaselineCannotBeImplicitlyActivatedByRepositoryDefaults()
            throws IOException {
        String configuration = Files.readString(Path.of("src/main/resources/application.yaml"));
        String baseline = Files.readString(Path.of("docs/database-baseline.md"));
        assertThat(configuration).contains("JOBTRACE_FLYWAY_ENABLED:false");
        assertThat(baseline).contains("**Not approved for production**")
                .contains("Before enabling Flyway");
        assertThat(Files.exists(Path.of("src/main/resources/db/migration"))).isFalse();
    }

    @Test
    void javaMarketplaceWebHasNoCollectorOrRunWriter() throws IOException {
        Path web = Path.of("src/main/java/com/jobtrace/jobmarket/web");
        try (var files = Files.list(web)) {
            var sources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }).toList();
            assertThat(sources).noneMatch(source -> source.contains("/api/internal/job-market/")
                    || source.contains("/api/admin/job-market/ats-site-scan")
                    || source.contains("/api/admin/job-market/company-research"));
            assertThat(sources).anyMatch(source -> source.contains("ingestion_owned_externally"));
        }
    }
}

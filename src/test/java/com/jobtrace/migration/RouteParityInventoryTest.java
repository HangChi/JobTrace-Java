package com.jobtrace.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RouteParityInventoryTest {
    private static final Pattern ROW = Pattern.compile("^\\| `([^`]+)` \\| ([SDL]) \\|.*");

    @Test
    void everyPinnedLegacyRouteHasExactlyOneExplicitDisposition() throws IOException {
        List<String> pinned = Files.readAllLines(Path.of(
                "src/test/resources/contracts/migration/legacy-api-routes.txt"));
        List<String> rows = Files.readAllLines(Path.of(
                "specs/029-code-hardening/contracts/route-matrix.md"));
        Map<String, String> dispositions = rows.stream()
                .map(ROW::matcher)
                .filter(matcher -> matcher.matches())
                .collect(Collectors.toMap(matcher -> matcher.group(1),
                        matcher -> matcher.group(2)));

        assertThat(pinned).hasSize(65).doesNotHaveDuplicates();
        assertThat(dispositions.keySet()).containsExactlyInAnyOrderElementsOf(pinned);
        assertThat(dispositions).containsEntry("admin/job-market/sources/[sourceId]/sync", "D")
                .containsEntry("internal/job-market/collect-wechat", "L")
                .containsEntry("job-market/campaigns", "S");
    }

    @Test
    void noJavaMarketplaceCollectorOrSchedulerIsIntroduced() throws IOException {
        Path web = Path.of("src/main/java/com/jobtrace/jobmarket/web");
        try (var files = Files.list(web)) {
            Set<String> javaSources = files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }).collect(Collectors.toSet());
            assertThat(javaSources).noneMatch(source -> source.contains("/api/internal/job-market/")
                    || source.contains("/api/admin/job-market/ats-site-scan")
                    || source.contains("/api/admin/job-market/company-research"));
        }
    }
}

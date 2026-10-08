package com.jobtrace.jobmarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.jobmarket.domain.SourceRegistration;
import com.jobtrace.jobmarket.web.SourceRegistrationRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceRegistrationTest {

    private static final UUID COMPANY = UUID.fromString(
            "10000000-0000-4000-8000-000000000001");

    @Test
    void acceptsApprovedHttpsHostAndNormalizesHostCase() {
        var value = registration("https://Jobs.Example.test/boards?department=eng",
                List.of("JOBS.EXAMPLE.TEST"));
        assertThat(value.allowedHosts()).containsExactly("jobs.example.test");
        assertThat(value.adapter()).isEqualTo("greenhouse");
    }

    @Test
    void rejectsUnsafeOrUnapprovedAddresses() {
        for (String url : List.of("http://jobs.example.test", "https://localhost/jobs",
                "https://127.0.0.1/jobs", "https://[::1]/jobs",
                "https://user:pass@jobs.example.test", "https://other.example.test")) {
            assertThatThrownBy(() -> registration(url, List.of("jobs.example.test")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsInvalidAdapterHostsAndSchedule() {
        assertThatThrownBy(() -> new SourceRegistration(COMPANY, "unknown", "key",
                "https://jobs.example.test", List.of("jobs.example.test"), List.of(),
                "public", true, 360)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registration("https://jobs.example.test",
                List.of("*.example.test"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRegistration(COMPANY, "greenhouse", "key",
                "https://jobs.example.test", List.of("jobs.example.test"), List.of(),
                "public", true, 59)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registration("https://jobs.example.test",
                List.of("jobs.example.test", "JOBS.EXAMPLE.TEST")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SourceRegistration(COMPANY, "greenhouse", "key",
                "https://jobs.example.test", List.of("jobs.example.test"), List.of("CH"),
                "public", true, 360)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingAndOverlongRegistrationFields() {
        assertThatThrownBy(() -> source(null, "greenhouse", "key", "https://jobs.example.test",
                List.of("jobs.example.test"), List.of(), "public", 360))
                .isInstanceOf(IllegalArgumentException.class);
        for (String key : new String[] {" ", "x".repeat(201)}) {
            assertThatThrownBy(() -> source(COMPANY, "greenhouse", key,
                    "https://jobs.example.test", List.of("jobs.example.test"),
                    List.of(), "public", 360)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> source(COMPANY, "greenhouse", null,
                "https://jobs.example.test", List.of("jobs.example.test"),
                List.of(), "public", 360)).isInstanceOf(IllegalArgumentException.class);
        for (String url : new String[] {null, "https://jobs.example.test/" + "x".repeat(2048)}) {
            assertThatThrownBy(() -> source(COMPANY, "greenhouse", "key", url,
                    List.of("jobs.example.test"), List.of(), "public", 360))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (List<String> hosts : new List[] {null, List.of(),
                java.util.Collections.nCopies(11, "jobs.example.test")}) {
            assertThatThrownBy(() -> source(COMPANY, "greenhouse", "key",
                    "https://jobs.example.test", hosts, List.of(), "public", 360))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsInvalidHostFormsCountryCodesAndAccess() {
        for (String host : new String[] {"", "a".repeat(254), "*.example.test",
                ".example.test", "example.test.", "a..example.test", "localhost",
                "a.localhost", "127.0.0.1"}) {
            assertThatThrownBy(() -> registration("https://jobs.example.test",
                    List.of(host))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> registration("https://jobs.example.test",
                java.util.Arrays.asList((String) null)))
                .isInstanceOf(IllegalArgumentException.class);
        for (List<String> countries : new List[] {null,
                java.util.Collections.nCopies(11, "cn"),
                java.util.Arrays.asList((String) null)}) {
            assertThatThrownBy(() -> source(COMPANY, "greenhouse", "key",
                    "https://jobs.example.test", List.of("jobs.example.test"),
                    countries, "public", 360)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> source(COMPANY, "greenhouse", "key",
                "https://jobs.example.test", List.of("jobs.example.test"),
                List.of(), "private", 360)).isInstanceOf(IllegalArgumentException.class);
        assertThat(source(COMPANY, "greenhouse", "key", "https://jobs.example.test",
                List.of("jobs.example.test"), List.of(), "authorized", 1440)
                .accessBasis()).isEqualTo("authorized");
        assertThatThrownBy(() -> source(COMPANY, "greenhouse", "key",
                "https://jobs.example.test", List.of("jobs.example.test"),
                List.of(), "public", 1441)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> registration("https://jobs.example.test/%zz",
                List.of("jobs.example.test"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestAppliesLegacyDefaults() {
        var request = new SourceRegistrationRequest(COMPANY, "greenhouse", "key",
                "https://jobs.example.test", List.of("jobs.example.test"),
                null, "public", null, null);
        var value = request.toDomain();
        assertThat(value.countryCodes()).isEmpty();
        assertThat(value.isOfficial()).isTrue();
        assertThat(value.syncIntervalMinutes()).isEqualTo(360);
        var explicit = new SourceRegistrationRequest(COMPANY, "greenhouse", "key",
                "https://jobs.example.test", List.of("jobs.example.test"),
                List.of("cn"), "public", false, 120).toDomain();
        assertThat(explicit.isOfficial()).isFalse();
        assertThat(explicit.syncIntervalMinutes()).isEqualTo(120);
    }

    private static SourceRegistration registration(String url, List<String> hosts) {
        return new SourceRegistration(COMPANY, "greenhouse", "key", url,
                hosts, List.of("cn"), "public", true, 360);
    }

    private static SourceRegistration source(UUID company, String adapter, String key,
            String url, List<String> hosts, List<String> countries, String basis, int interval) {
        return new SourceRegistration(company, adapter, key, url, hosts,
                countries, basis, true, interval);
    }
}

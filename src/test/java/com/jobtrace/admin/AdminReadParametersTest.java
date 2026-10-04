package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.admin.domain.AdminQuery;
import com.jobtrace.admin.web.AdminReadParameters;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

class AdminReadParametersTest {

    @Test
    void appliesLegacyDefaultsAndNormalizesUserFilters() {
        var values = new LinkedMultiValueMap<String, String>();
        values.add("q", "  Alice  ");
        values.add("role", "admin");
        values.add("status", "disabled");
        values.add("registeredFrom", "2026-09-01");
        AdminQuery.Users query = AdminReadParameters.users(values);

        assertThat(query.q()).isEqualTo("Alice");
        assertThat(query.role()).isEqualTo(AdminQuery.UserRole.ADMIN);
        assertThat(query.status()).isEqualTo(AdminQuery.UserStatus.DISABLED);
        assertThat(query.registeredFrom()).hasToString("2026-09-01");
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.limit()).isEqualTo(50);
    }

    @Test
    void validatesBoundsEnumsDatesAndOrderedRanges() {
        assertInvalid("limit", "101");
        assertInvalid("page", "0");
        assertInvalid("role", "owner");
        assertInvalid("registeredFrom", "2026-02-30");
        var reversed = new LinkedMultiValueMap<String, String>();
        reversed.add("registeredFrom", "2026-10-02");
        reversed.add("registeredTo", "2026-10-01");
        assertThatThrownBy(() -> AdminReadParameters.users(reversed))
                .isInstanceOf(IllegalArgumentException.class);
        assertInvalid("q", "x".repeat(101));
    }

    @Test
    void parsesAuditAndIndependentContentPages() {
        var values = new LinkedMultiValueMap<String, String>();
        values.add("actor", " admin@example.test ");
        values.add("eventType", "disable_user");
        values.add("outcome", "conflict");
        values.add("applicationsPage", "2");
        values.add("interviewsPage", "3");

        AdminQuery.Audit audit = AdminReadParameters.audit(values);
        assertThat(audit.actor()).isEqualTo("admin@example.test");
        assertThat(audit.eventType()).isEqualTo(AdminQuery.EventType.DISABLE_USER);
        assertThat(audit.outcome()).isEqualTo(AdminQuery.Outcome.CONFLICT);
        assertThat(AdminReadParameters.content(values))
                .isEqualTo(new AdminQuery.Content(2, 3));
    }

    private static void assertInvalid(String name, String value) {
        var values = new LinkedMultiValueMap<String, String>();
        values.add(name, value);
        assertThatThrownBy(() -> AdminReadParameters.users(values))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

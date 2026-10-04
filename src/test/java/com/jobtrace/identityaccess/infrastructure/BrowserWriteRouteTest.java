package com.jobtrace.identityaccess.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrowserWriteRouteTest {
    @Test
    void onlyExactExistingAccountAndAdminWritesCanUseBrowserCsrfPolicy() {
        for (String route : new String[] {
                "PATCH /api/profile", "POST /api/profile/password",
                "DELETE /api/profile/email", "PATCH /api/profile/email",
                "POST /api/profile/email/code", "DELETE /api/profile/sessions",
                "PATCH /api/admin/users/user-a"}) {
            String[] parts = route.split(" ", 2);
            assertThat(BrowserIdentityAccessConfiguration.browserWrite(parts[0], parts[1]))
                    .as(route).isTrue();
        }
        for (String route : new String[] {
                "GET /api/profile", "POST /api/profile",
                "POST /api/admin/users/user-a", "PATCH /api/admin/users/user-a/extra",
                "PATCH /api/admin/users/" + "x".repeat(129),
                "POST /api/auth/login", "PATCH /api/reminders/unknown"}) {
            String[] parts = route.split(" ", 2);
            assertThat(BrowserIdentityAccessConfiguration.browserWrite(parts[0], parts[1]))
                    .as(route).isFalse();
        }
    }
}

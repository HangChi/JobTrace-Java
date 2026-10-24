package com.jobtrace.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.admin.domain.AdminAccessAction;
import com.jobtrace.admin.domain.AdminAccessChange;
import com.jobtrace.admin.domain.InvalidAdminAccessCommandException;
import org.junit.jupiter.api.Test;

class AdminAccessCommandTest {

    @Test
    void parsesAndTrimsACompleteCommand() {
        var command = AdminAccessChange.parse(
                "0199a55c-9b00-7000-8000-000000000010",
                2L,
                "disable_user",
                "  Confirmed security incident  ",
                null);

        assertThat(command.action()).isEqualTo(AdminAccessAction.DISABLE_USER);
        assertThat(command.reason()).isEqualTo("Confirmed security incident");
        assertThat(command.confirmSelf()).isFalse();
    }

    @Test
    void rejectsMalformedFields() {
        assertThatThrownBy(() -> AdminAccessChange.parse(
                "not-a-uuid", 1L, "disable_user", "Long enough reason", false))
                .isInstanceOf(InvalidAdminAccessCommandException.class);
        assertThatThrownBy(() -> AdminAccessChange.parse(
                "0199a55c-9b00-7000-8000-000000000010",
                0L, "disable_user", "Long enough reason", false))
                .isInstanceOf(InvalidAdminAccessCommandException.class);
        assertThatThrownBy(() -> AdminAccessChange.parse(
                "0199a55c-9b00-7000-8000-000000000010",
                1L, "delete_user", "Long enough reason", false))
                .isInstanceOf(InvalidAdminAccessCommandException.class);
        assertThatThrownBy(() -> AdminAccessChange.parse(
                "0199a55c-9b00-7000-8000-000000000010",
                1L, "disable_user", "short", false))
                .isInstanceOf(InvalidAdminAccessCommandException.class);
    }
}

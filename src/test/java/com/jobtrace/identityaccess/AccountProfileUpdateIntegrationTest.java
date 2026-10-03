package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.UpdateAccountProfile;
import com.jobtrace.identityaccess.domain.AccountProfileNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountProfileUpdateIntegrationTest extends AccountProfileDatabaseTest {

    @Autowired
    private UpdateAccountProfile updateProfile;

    @Test
    void updatesOnlyOwnerAndReturnsUnchangedRecoveryProjection() {
        var result = updateProfile.execute(OWNER, "  New Name  ", "https://img.test/new.png");
        assertThat(result.id()).isEqualTo(OWNER);
        assertThat(result.displayName()).isEqualTo("New Name");
        assertThat(result.image()).isEqualTo("https://img.test/new.png");
        assertThat(result.recoveryEmail()).isEqualTo("owner@example.test");
        assertThat(result.emailVerified()).isTrue();
        assertThat(profile(OTHER_OWNER)).containsExactly(
                "Other Name", "https://img.test/other.png");
    }

    @Test
    void clearsImageAndMissingOwnerDoesNotMutateAnything() {
        assertThat(updateProfile.execute(OWNER, "Name", "").image()).isNull();
        assertThatThrownBy(() -> updateProfile.execute("missing", "Missing", null))
                .isInstanceOf(AccountProfileNotFoundException.class);
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(2);
    }

    private java.util.List<String> profile(String id) {
        return jdbc.query("select display_name,image from users where id=?", (row, index) ->
                java.util.List.of(row.getString("display_name"), row.getString("image")), id)
                .getFirst();
    }
}

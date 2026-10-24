package com.jobtrace.identityaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrace.identityaccess.application.UpdateAccountProfile;
import com.jobtrace.identityaccess.domain.AccountProfileNotFoundException;
import com.jobtrace.identityaccess.domain.AccountProfileResult;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UpdateAccountProfileTest {

    @Test
    void normalizesNameAndImageAndReturnsCommandResult() {
        var observed = new AtomicReference<com.jobtrace.identityaccess.domain.AccountProfileUpdate>();
        var useCase = new UpdateAccountProfile(command -> {
            observed.set(command);
            return Optional.of(new AccountProfileResult(
                    command.ownerId(), command.displayName(), command.image(), null, false));
        });
        assertThat(useCase.execute("owner-a", "  New Name  ", "https://img.test/a.png"))
                .isEqualTo(new AccountProfileResult(
                        "owner-a", "New Name", "https://img.test/a.png", null, false));
        assertThat(observed.get().displayName()).isEqualTo("New Name");
    }

    @Test
    void nullAndEmptyImagesBothClearAvatar() {
        var observed = new AtomicReference<String>("initial");
        var useCase = new UpdateAccountProfile(command -> {
            observed.set(command.image());
            return Optional.of(new AccountProfileResult(
                    command.ownerId(), command.displayName(), command.image(), null, false));
        });
        assertThat(useCase.execute("owner", "Name", null).image()).isNull();
        assertThat(observed.get()).isNull();
        assertThat(useCase.execute("owner", "Name", "").image()).isNull();
    }

    @Test
    void rejectsInvalidInputsBeforeMutation() {
        int[] calls = {0};
        var useCase = new UpdateAccountProfile(command -> {
            calls[0]++;
            return Optional.empty();
        });
        for (String owner : new String[] {null, "", " ", "x".repeat(129)}) {
            assertThatThrownBy(() -> useCase.execute(owner, "Name", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String name : new String[] {null, "", " ", "x".repeat(101)}) {
            assertThatThrownBy(() -> useCase.execute("owner", name, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String image : new String[] {"relative.png", "not a url"}) {
            assertThatThrownBy(() -> useCase.execute("owner", "Name", image))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(calls[0]).isZero();
    }

    @Test
    void missingOwnerUsesSafeNotFoundException() {
        var useCase = new UpdateAccountProfile(command -> Optional.empty());
        assertThatThrownBy(() -> useCase.execute("missing", "Name", null))
                .isInstanceOf(AccountProfileNotFoundException.class)
                .hasMessage("没有找到账号资料。");
    }
}

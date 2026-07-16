package com.platform.analytics.security;

import com.platform.analytics.config.YouTubeProperties;
import com.platform.analytics.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateTokenServiceTest {

    private StateTokenService stateTokenService;

    @BeforeEach
    void setUp() {
        YouTubeProperties properties = new YouTubeProperties();
        properties.setStateSecret("unit-test-secret");
        stateTokenService = new StateTokenService(properties);
    }

    @Test
    void generateThenVerify_roundTripsToOriginalUserId() {
        UUID userId = UUID.randomUUID();

        String state = stateTokenService.generateState(userId);
        UUID extracted = stateTokenService.verifyAndExtractUserId(state);

        assertThat(extracted).isEqualTo(userId);
    }

    @Test
    void verify_rejectsTamperedState() {
        UUID userId = UUID.randomUUID();
        String state = stateTokenService.generateState(userId);

        // Flip a character in the middle of the string rather than the
        // last one: base64url without padding can leave the final
        // character's unused low-order bits insignificant to the decoded
        // byte value, so mutating it can (depending on length mod 4)
        // accidentally decode to the same bytes and not actually tamper
        // with anything. A middle character sits inside a full 4-char
        // group and always maps onto real payload/signature bytes.
        int flipIndex = state.length() / 2;
        char original = state.charAt(flipIndex);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = state.substring(0, flipIndex) + replacement + state.substring(flipIndex + 1);

        assertThatThrownBy(() -> stateTokenService.verifyAndExtractUserId(tampered))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void verify_rejectsGarbageInput() {
        assertThatThrownBy(() -> stateTokenService.verifyAndExtractUserId("not-a-valid-state"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void differentSecrets_produceIncompatibleStates() {
        UUID userId = UUID.randomUUID();
        String state = stateTokenService.generateState(userId);

        YouTubeProperties otherProperties = new YouTubeProperties();
        otherProperties.setStateSecret("a-completely-different-secret");
        StateTokenService otherService = new StateTokenService(otherProperties);

        assertThatThrownBy(() -> otherService.verifyAndExtractUserId(state))
                .isInstanceOf(UnauthorizedException.class);
    }
}

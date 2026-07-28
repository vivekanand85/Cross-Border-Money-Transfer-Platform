package com.moneytransfer.orchestration_service.statemachine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


class TransferStateTest {

    @Test
    void initiated_canOnlyMoveTo_screeningOrFailed() {
        assertLegal(TransferState.INITIATED, TransferState.SCREENING, TransferState.FAILED);
    }

    @Test
    void screening_canOnlyMoveTo_pendingReviewOrPayInOrFailed() {
        assertLegal(TransferState.SCREENING, TransferState.PENDING_REVIEW, TransferState.PAY_IN, TransferState.FAILED);
    }

    @Test
    void pendingReview_canOnlyMoveTo_payInOrFailed() {
        assertLegal(TransferState.PENDING_REVIEW, TransferState.PAY_IN, TransferState.FAILED);
    }

    @Test
    void payIn_canOnlyMoveTo_payOutOrFailedOrReversed() {
        assertLegal(TransferState.PAY_IN, TransferState.PAY_OUT, TransferState.FAILED, TransferState.REVERSED);
    }

    @Test
    void payOut_canOnlyMoveTo_settledOrFailedOrReversedOrAwaitingPickup() {
        assertLegal(TransferState.PAY_OUT, TransferState.SETTLED, TransferState.FAILED,
                TransferState.REVERSED, TransferState.AWAITING_PICKUP);
    }

    @ParameterizedTest
    @MethodSource("terminalStates")
    void terminalStates_haveNoLegalOutgoingTransitions(TransferState terminalState) {
        assertThat(terminalState.allowedNextStates()).isEmpty();

        for (TransferState target : TransferState.values()) {
            assertThat(terminalState.canTransitionTo(target))
                    .as("%s should never be able to transition to %s", terminalState, target)
                    .isFalse();
        }
    }

    static Stream<Arguments> terminalStates() {
        return Stream.of(
                Arguments.of(TransferState.SETTLED),
                Arguments.of(TransferState.FAILED),
                Arguments.of(TransferState.REVERSED)
        );
    }

    @Test
    void initiated_cannotJumpDirectlyToSettled() {
        // The specific illegal jump we manually verified via Postman earlier —
        // now locked in as a permanent regression test.
        assertThat(TransferState.INITIATED.canTransitionTo(TransferState.SETTLED)).isFalse();
    }

    @Test
    void screening_cannotJumpDirectlyToSettled() {
        assertThat(TransferState.SCREENING.canTransitionTo(TransferState.SETTLED)).isFalse();
    }

    @Test
    void noState_canTransitionToItself() {

        for (TransferState state : TransferState.values()) {
            assertThat(state.canTransitionTo(state))
                    .as("%s should not be able to transition to itself", state)
                    .isFalse();
        }
    }

    
    private void assertLegal(TransferState fromState, TransferState... expectedLegalTargets) {
        EnumSet<TransferState> expected = EnumSet.noneOf(TransferState.class);
        for (TransferState s : expectedLegalTargets) {
            expected.add(s);
        }

        assertThat(fromState.allowedNextStates()).isEqualTo(expected);

        for (TransferState target : TransferState.values()) {
            boolean shouldBeLegal = expected.contains(target);
            assertThat(fromState.canTransitionTo(target))
                    .as("%s -> %s should be %s", fromState, target, shouldBeLegal ? "LEGAL" : "ILLEGAL")
                    .isEqualTo(shouldBeLegal);
        }
    }
}
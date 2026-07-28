package com.moneytransfer.orchestration_service.statemachine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AwaitingPickupStateTest {

    @Test
    void payOut_canMoveToAwaitingPickup() {
        assertThat(TransferState.PAY_OUT.canTransitionTo(TransferState.AWAITING_PICKUP)).isTrue();
    }

    @Test
    void awaitingPickup_canMoveToSettledFailedOrReversed() {
        assertThat(TransferState.AWAITING_PICKUP.canTransitionTo(TransferState.SETTLED)).isTrue();
        assertThat(TransferState.AWAITING_PICKUP.canTransitionTo(TransferState.FAILED)).isTrue();
        assertThat(TransferState.AWAITING_PICKUP.canTransitionTo(TransferState.REVERSED)).isTrue();
    }

    @Test
    void awaitingPickup_cannotJumpBackToPayOut() {
        assertThat(TransferState.AWAITING_PICKUP.canTransitionTo(TransferState.PAY_OUT)).isFalse();
    }

    @Test
    void initiated_cannotJumpDirectlyToAwaitingPickup() {
        assertThat(TransferState.INITIATED.canTransitionTo(TransferState.AWAITING_PICKUP)).isFalse();
    }
}
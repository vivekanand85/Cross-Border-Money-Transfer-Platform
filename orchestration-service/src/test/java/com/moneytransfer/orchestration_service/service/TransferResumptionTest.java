package com.moneytransfer.orchestration_service.service;

import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.entity.TransferStateTransition;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerClient;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerTransactionResponse;
import com.moneytransfer.orchestration_service.repo.TransferRepository;
import com.moneytransfer.orchestration_service.repo.TransferStateTransitionRepository;
import com.moneytransfer.orchestration_service.statemachine.TransferState;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * THE central proof of this whole design: a transfer's progress through the
 * saga is never held only in memory — see the class-level explanation from
 * the original version of this test for the full reasoning.
 *
 * UPDATED: TransferOrchestrationService now calls a real Ledger service via
 * LedgerClient when transitioning to PAY_IN. This test is about proving
 * ORCHESTRATION's own resumption behavior, not Ledger's — so LedgerClient
 * is mocked here (@MockBean) rather than requiring a real Ledger instance
 * to be running alongside this test. A separate, dedicated integration test
 * (not this one) would be the right place to prove the real end-to-end call
 * to a live Ledger service.
 */
@SpringBootTest
@Testcontainers
class TransferResumptionTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("orchestration_db")
            .withUsername("orchestration_user")
            .withPassword("orchestration_pass");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransferOrchestrationService transferOrchestrationService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransferStateTransitionRepository transitionRepository;

    // Replaces the real LedgerClient bean with a Mockito mock for this test
    // context only. No real HTTP call happens — postTransaction() just
    // returns a canned response, so PAY_IN transitions succeed without a
    // running Ledger instance.
    @MockBean
    private LedgerClient ledgerClient;

    @Test
    void transferState_survivesSimulatedCrash_becauseItIsPersistedNotHeldInMemory() {

        when(ledgerClient.postTransaction(any(), any(), any(), any(), any(), any()))
                .thenReturn(new LedgerTransactionResponse(UUID.randomUUID(), "stubbed-key", "TRANSFER_PAY_IN", "INR", "POSTED"));

        // --- "Before the crash": drive the transfer partway through the saga ---
        Transfer created = transferOrchestrationService.initiateTransfer(
                "resume-key-1", 7500L, "INR", UUID.randomUUID(), UUID.randomUUID());

        transferOrchestrationService.transitionTo(created.getId(), TransferState.SCREENING, "SYSTEM", null);
        transferOrchestrationService.transitionTo(created.getId(), TransferState.PAY_IN, "SYSTEM", null);

        UUID transferId = created.getId();

        // --- "The crash": no in-memory saga object exists to lose. Prove it
        // by going back to the DB fresh through a plain repository call. ---
        Transfer reloaded = transferRepository.findById(transferId)
                .orElseThrow(() -> new AssertionError("Transfer vanished — state was not actually persisted!"));

        assertThat(reloaded.getCurrentState()).isEqualTo(TransferState.PAY_IN);
        assertThat(reloaded.getAmount()).isEqualTo(7500L);
        assertThat(reloaded.getIdempotencyKey()).isEqualTo("resume-key-1");

        List<TransferStateTransition> history =
                transitionRepository.findByTransferIdOrderByCreatedAtAsc(transferId);

        assertThat(history).hasSize(3); // null->INITIATED, INITIATED->SCREENING, SCREENING->PAY_IN
        assertThat(history.get(0).getToState()).isEqualTo(TransferState.INITIATED);
        assertThat(history.get(1).getToState()).isEqualTo(TransferState.SCREENING);
        assertThat(history.get(2).getToState()).isEqualTo(TransferState.PAY_IN);

        // --- Resuming the saga from here works exactly like any other
        // transition call — there is no special "resume" method. ---
        Transfer resumed = transferOrchestrationService.transitionTo(
                transferId, TransferState.PAY_OUT, "SYSTEM_RESUMED_AFTER_RESTART", null);

        assertThat(resumed.getCurrentState()).isEqualTo(TransferState.PAY_OUT);
    }
}
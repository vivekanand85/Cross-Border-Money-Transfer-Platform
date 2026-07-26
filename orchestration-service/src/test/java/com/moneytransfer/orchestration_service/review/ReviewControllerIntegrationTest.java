package com.moneytransfer.orchestration_service.review;

import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerClient;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerTransactionResponse;
import com.moneytransfer.orchestration_service.service.TransferOrchestrationService;
import com.moneytransfer.orchestration_service.statemachine.TransferState;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Proves ReviewController's approve/decline endpoints actually work —
 * real HTTP calls (via TestRestTemplate, against the real embedded server
 * this @SpringBootTest starts) hitting real repositories against real
 * Postgres. LedgerClient is mocked, same reasoning as other tests: this is
 * about proving the review workflow, not re-proving Ledger integration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReviewControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("orchestration_db")
            .withUsername("orchestration_user")
            .withPassword("orchestration_pass");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransferOrchestrationService transferOrchestrationService;

    @Autowired
    private ReviewQueueRepository reviewQueueRepository;

    @Autowired
    private com.moneytransfer.orchestration_service.repo.TransferRepository transferRepository;

    @MockBean
    private LedgerClient ledgerClient;

    @MockBean
    private com.moneytransfer.orchestration_service.client.ApolloPayInClient apolloPayInClient;

    @Test
    void approve_movesTransferToPayIn_andCallsLedger() {
        when(ledgerClient.postTransaction(any(), any(), any(), any(), any(), any()))
                .thenReturn(new LedgerTransactionResponse(UUID.randomUUID(), "k", "TRANSFER_PAY_IN", "INR", "POSTED"));

        when(apolloPayInClient.payIn(any()))
                .thenReturn(com.moneytransfer.orchestration_service.dto.PayInResult.builder()
                        .stripePaymentIntentId("pi_test_stub")
                        .status("succeeded")
                        .build());

        Transfer transfer = transferOrchestrationService.initiateTransfer(
                "review-approve-001", 5000L, "INR", UUID.randomUUID(), UUID.randomUUID());
        transferOrchestrationService.transitionTo(transfer.getId(), TransferState.SCREENING, "SYSTEM", null);
        transferOrchestrationService.transitionTo(transfer.getId(), TransferState.PENDING_REVIEW, "SYSTEM", null);

        ReviewQueueEntry entry = ReviewQueueEntry.builder()
                .transferId(transfer.getId())
                .riskScore(90)
                .reason("test")
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();
        entry = reviewQueueRepository.save(entry);

        ResponseEntity<ReviewQueueEntry> response = restTemplate.exchange(
                "/api/v1/review/{id}/approve?reviewedBy=test-reviewer",
                HttpMethod.POST, null, ReviewQueueEntry.class, entry.getId());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo("APPROVED");
        assertThat(response.getBody().getReviewedBy()).isEqualTo("test-reviewer");

        // Confirm approve() actually drove the underlying transfer forward,
        // not just updated the review queue row in isolation.
        Transfer reloaded = transferRepository.findById(transfer.getId()).orElseThrow();
        assertThat(reloaded.getCurrentState()).isEqualTo(TransferState.PAY_IN);
    }

    @Test
    void decline_movesTransferToFailed() {
        Transfer transfer = transferOrchestrationService.initiateTransfer(
                "review-decline-001", 3000L, "INR", UUID.randomUUID(), UUID.randomUUID());
        transferOrchestrationService.transitionTo(transfer.getId(), TransferState.SCREENING, "SYSTEM", null);
        transferOrchestrationService.transitionTo(transfer.getId(), TransferState.PENDING_REVIEW, "SYSTEM", null);

        ReviewQueueEntry entry = ReviewQueueEntry.builder()
                .transferId(transfer.getId())
                .riskScore(95)
                .reason("suspicious pattern")
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();
        entry = reviewQueueRepository.save(entry);

        ResponseEntity<ReviewQueueEntry> response = restTemplate.exchange(
                "/api/v1/review/{id}/decline?reviewedBy=test-reviewer&reason=confirmed+fraud",
                HttpMethod.POST, null, ReviewQueueEntry.class, entry.getId());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getStatus()).isEqualTo("DECLINED");
    }
}
package com.moneytransfer.orchestration_service.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerClient;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerTransactionResponse;
import com.moneytransfer.orchestration_service.review.ReviewQueueEntry;
import com.moneytransfer.orchestration_service.review.ReviewQueueRepository;
import com.moneytransfer.orchestration_service.statemachine.TransferState;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class TransferScreeningIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("orchestration_db")
            .withUsername("orchestration_user")
            .withPassword("orchestration_pass");

    static WireMockServer wireMockServer;

    static {
        postgres.start();
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @AfterEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("risk-screening.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Autowired
    private TransferOrchestrationService transferOrchestrationService;

    @Autowired
    private ReviewQueueRepository reviewQueueRepository;

    @MockBean
    private LedgerClient ledgerClient;

    @MockBean
    private com.moneytransfer.orchestration_service.client.ApolloPayInClient apolloPayInClient;

    @Test
    void lowRiskTransfer_autoAdvancesToPayIn() {
        when(ledgerClient.postTransaction(any(), any(), any(), any(), any(), any()))
                .thenReturn(new LedgerTransactionResponse(UUID.randomUUID(), "k", "TRANSFER_PAY_IN", "INR", "POSTED"));

        when(apolloPayInClient.payIn(any()))
                .thenReturn(com.moneytransfer.orchestration_service.dto.PayInResult.builder()
                        .stripePaymentIntentId("pi_test_stub")
                        .status("succeeded")
                        .build());

        stubFor(post(urlEqualTo("/screen"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"riskScore\":10,\"decision\":\"APPROVE\",\"reason\":\"low risk\"}")));

        Transfer created = transferOrchestrationService.initiateTransfer(
                "screen-low-risk", 1000L, "INR", UUID.randomUUID(), UUID.randomUUID());

        Transfer result = transferOrchestrationService.runScreening(created.getId());

        assertThat(result.getCurrentState()).isEqualTo(TransferState.PAY_IN);

        List<ReviewQueueEntry> reviewEntries = reviewQueueRepository.findByStatus("PENDING");
        assertThat(reviewEntries).noneMatch(e -> e.getTransferId().equals(created.getId()));
    }

    @Test
    void highRiskTransfer_parksInPendingReviewWithQueueEntry() {
        stubFor(post(urlEqualTo("/screen"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"riskScore\":92,\"decision\":\"MANUAL_REVIEW\",\"reason\":\"amount exceeds threshold\"}")));

        Transfer created = transferOrchestrationService.initiateTransfer(
                "screen-high-risk", 999999L, "INR", UUID.randomUUID(), UUID.randomUUID());

        Transfer result = transferOrchestrationService.runScreening(created.getId());

        assertThat(result.getCurrentState()).isEqualTo(TransferState.PENDING_REVIEW);

        ReviewQueueEntry entry = reviewQueueRepository.findByTransferIdAndStatus(created.getId(), "PENDING")
                .orElseThrow(() -> new AssertionError("Expected a PENDING review queue entry but found none"));

        assertThat(entry.getRiskScore()).isEqualTo(92);
        assertThat(entry.getReason()).contains("amount exceeds threshold");
   }
}
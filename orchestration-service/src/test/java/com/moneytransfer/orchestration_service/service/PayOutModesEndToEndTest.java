package com.moneytransfer.orchestration_service.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moneytransfer.orchestration_service.client.ApolloPayInClient;
import com.moneytransfer.orchestration_service.dto.PayInResult;
import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerClient;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerTransactionResponse;
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

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Full end-to-end proof for all three payout modes, replacing the manual
 * Postman walkthrough with an automated, repeatable test. LedgerClient and
 * ApolloPayInClient are mocked (already proven independently); APN is
 * WireMock, matching production reality (APN has no public sandbox).
 */
@SpringBootTest
@Testcontainers
class PayOutModesEndToEndTest {

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
        registry.add("apn.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    @Autowired
    private TransferOrchestrationService transferOrchestrationService;

    @MockBean
    private LedgerClient ledgerClient;

    @MockBean
    private ApolloPayInClient apolloPayInClient;

    private void stubPayIn() {
        when(apolloPayInClient.payIn(any()))
                .thenReturn(PayInResult.builder().stripePaymentIntentId("pi_test").status("succeeded").build());
        when(ledgerClient.postTransaction(any(), any(), any(), any(), any(), any()))
                .thenReturn(new LedgerTransactionResponse(UUID.randomUUID(), "k", "TRANSFER", "INR", "POSTED"));
    }

    private Transfer driveToPy(String idempotencyKey, String payoutMode) {
        stubPayIn();
        Transfer t = transferOrchestrationService.initiateTransfer(
                idempotencyKey, 50000L, "INR", UUID.randomUUID(), UUID.randomUUID(), payoutMode);
        transferOrchestrationService.transitionTo(t.getId(), TransferState.SCREENING, "SYSTEM", null);
        transferOrchestrationService.transitionTo(t.getId(), TransferState.PAY_IN, "SYSTEM", null);
        return t;
    }

    @Test
    void bankPayout_settlesImmediately() {
        stubFor(post(urlEqualTo("/payout")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"apnReferenceId\":\"ref1\",\"status\":\"SETTLED\",\"pickupCode\":null}")));

        Transfer t = driveToPy("e2e-bank-001", "BANK");

        Transfer result = transferOrchestrationService.runPayOut(t.getId());
        assertThat(result.getCurrentState()).isEqualTo(TransferState.SETTLED);
    }

    @Test
    void walletPayout_settlesImmediately() {
        stubFor(post(urlEqualTo("/payout")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"apnReferenceId\":\"ref2\",\"status\":\"SETTLED\",\"pickupCode\":null}")));

        Transfer t = driveToPy("e2e-wallet-001", "WALLET");

        Transfer result = transferOrchestrationService.runPayOut(t.getId());
        assertThat(result.getCurrentState()).isEqualTo(TransferState.SETTLED);
    }

    @Test
    void cashPayout_waitsForPickupThenSettles() {
        stubFor(post(urlEqualTo("/payout")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"apnReferenceId\":\"ref3\",\"status\":\"AWAITING_PICKUP\",\"pickupCode\":\"PICKUP-999\"}")));

        Transfer t = driveToPy("e2e-cash-001", "CASH");

        Transfer afterPayout = transferOrchestrationService.runPayOut(t.getId());
        assertThat(afterPayout.getCurrentState()).isEqualTo(TransferState.AWAITING_PICKUP);

        org.mockito.Mockito.verify(ledgerClient, org.mockito.Mockito.times(1))
                .postTransaction(any(), any(), any(), any(), any(), any());

        Transfer settled = transferOrchestrationService.confirmPickup(t.getId(), "agent-01");
        assertThat(settled.getCurrentState()).isEqualTo(TransferState.SETTLED);

        org.mockito.Mockito.verify(ledgerClient, org.mockito.Mockito.times(2))
                .postTransaction(any(), any(), any(), any(), any(), any());
    }
}
package com.moneytransfer.orchestration_service.service;

import com.moneytransfer.orchestration_service.client.ApolloPayInClient;
import com.moneytransfer.orchestration_service.dto.PayInResult;
import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerClient;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerTransactionResponse;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@SpringBootTest
@Testcontainers
class PayInIdempotencyTest {

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
    private TransferOrchestrationService transferOrchestrationService;

    @MockBean
    private LedgerClient ledgerClient;

    @MockBean
    private ApolloPayInClient apolloPayInClient;

    @Test
    void samePayInTransitionCalledTwice_sendsIdenticalIdempotencyKeyBothTimes() {
        when(ledgerClient.postTransaction(any(), any(), any(), any(), any(), any()))
                .thenReturn(new LedgerTransactionResponse(UUID.randomUUID(), "k", "TRANSFER_PAY_IN", "INR", "POSTED"));

        when(apolloPayInClient.payIn(any()))
                .thenReturn(PayInResult.builder().stripePaymentIntentId("pi_test").status("succeeded").build());

        Transfer transfer = transferOrchestrationService.initiateTransfer(
                "double-payin-key", 50000L, "INR", UUID.randomUUID(), UUID.randomUUID(), "BANK");
        transferOrchestrationService.transitionTo(transfer.getId(), TransferState.SCREENING, "SYSTEM", null);

        String expectedKey = transfer.getId() + "-PAY_IN";

        // First attempt succeeds normally.
        transferOrchestrationService.transitionTo(transfer.getId(), TransferState.PAY_IN, "SYSTEM", null);

       
        try {
            transferOrchestrationService.transitionTo(transfer.getId(), TransferState.PAY_IN, "SYSTEM", null);
        } catch (Exception ignoredExpectedIllegalTransition) {
            // expected — see note above
        }

        verify(apolloPayInClient, times(1))
                .payIn(org.mockito.ArgumentMatchers.argThat(req -> req.getIdempotencyKey().equals(expectedKey)));
        verify(ledgerClient, times(1)).postTransaction(
                eq(expectedKey), any(), any(), any(), any(), any());
    }
}
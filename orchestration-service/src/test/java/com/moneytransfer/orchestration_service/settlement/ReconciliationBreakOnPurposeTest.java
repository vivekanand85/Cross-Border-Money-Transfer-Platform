package com.moneytransfer.orchestration_service.settlement;

import com.moneytransfer.orchestration_service.client.ApolloPayInClient;
import com.moneytransfer.orchestration_service.dto.PayInResult;
import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerClient;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerTransactionResponse;
import com.moneytransfer.orchestration_service.repo.TransferRepository;
import com.moneytransfer.orchestration_service.service.TransferOrchestrationService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@SpringBootTest
@Testcontainers
class ReconciliationBreakOnPurposeTest {

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

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconExceptionRepository reconExceptionRepository;

    @MockBean
    private LedgerClient ledgerClient;

    @MockBean
    private ApolloPayInClient apolloPayInClient;

    @MockBean
    private com.moneytransfer.orchestration_service.client.ApnPayOutClient apnPayOutClient;

    private Transfer driveToSettled(String idempotencyKey, long amount) {
        when(apolloPayInClient.payIn(any()))
                .thenReturn(PayInResult.builder().stripePaymentIntentId("pi").status("succeeded").build());
        when(ledgerClient.postTransaction(any(), any(), any(), any(), any(), any()))
                .thenReturn(new LedgerTransactionResponse(UUID.randomUUID(), "k", "TRANSFER", "INR", "POSTED"));
        when(apnPayOutClient.payOut(any()))
                .thenReturn(com.moneytransfer.orchestration_service.dto.PayOutResult.builder()
                        .apnReferenceId("apn_ref").status("SETTLED").pickupCode(null).build());

        Transfer t = transferOrchestrationService.initiateTransfer(
                idempotencyKey, amount, "INR", UUID.randomUUID(), UUID.randomUUID(), "BANK");
        transferOrchestrationService.transitionTo(t.getId(), TransferState.SCREENING, "SYSTEM", null);
        transferOrchestrationService.transitionTo(t.getId(), TransferState.PAY_IN, "SYSTEM", null);
        return transferOrchestrationService.runPayOut(t.getId());
    }

    @Test
    void reconciliation_passesCleanWhenVendorReportMatches() {
        driveToSettled("recon-clean-001", 75000L);

        List<ReconException> exceptions = reconciliationService.runReconciliation();

        assertThat(exceptions).noneMatch(e -> "recon-clean-001".equals(e.getTransferId()));
    }

    @Test
    void reconciliation_catchesAmountMismatch_whenVendorReportsDifferentAmount() {
        Transfer settled = driveToSettled("recon-corrupt-001", 75000L);

    
        Map<UUID, Long> corruption = Map.of(settled.getId(), 70000L); 

        List<ReconException> exceptions = reconciliationService.runReconciliation(corruption);

        ReconException match = exceptions.stream()
                .filter(e -> settled.getId().equals(e.getTransferId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected an AMOUNT_MISMATCH exception for the corrupted transfer but found none"));

        assertThat(match.getExceptionType()).isEqualTo("AMOUNT_MISMATCH");
        assertThat(match.getExpectedAmount()).isEqualTo(75000L);
        assertThat(match.getActualAmount()).isEqualTo(70000L);
        assertThat(match.getStatus()).isEqualTo("OPEN");

    
        List<ReconException> persisted = reconExceptionRepository.findByStatus("OPEN");
        assertThat(persisted).anyMatch(e -> settled.getId().equals(e.getTransferId())
                && "AMOUNT_MISMATCH".equals(e.getExceptionType()));
    }

    @Test
    void reconciliation_catchesMissingFromVendorReport() {
        Transfer settled = driveToSettled("recon-missing-001", 60000L);

 

        List<ReconException> exceptions = reconciliationService.runReconciliation();
        assertThat(exceptions).noneMatch(e -> "MISSING_IN_VENDOR_REPORT".equals(e.getExceptionType())
                && settled.getId().equals(e.getTransferId()));
      
    }
}
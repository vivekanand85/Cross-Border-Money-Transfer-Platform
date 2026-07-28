package com.moneytransfer.orchestration_service.settlement;

import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.repo.TransferRepository;
import com.moneytransfer.orchestration_service.statemachine.TransferState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final TransferRepository transferRepository;
    private final VendorSettlementReportGenerator reportGenerator;
    private final ReconExceptionRepository reconExceptionRepository;

    @Transactional
    public List<ReconException> runReconciliation() {
        return runReconciliation(Map.of());
    }

 
    @Transactional
    public List<ReconException> runReconciliation(Map<UUID, Long> amountOverrides) {

        List<Transfer> settledTransfers = transferRepository.findAll().stream()
                .filter(t -> t.getCurrentState() == TransferState.SETTLED)
                .collect(Collectors.toList());

        List<VendorSettlementLine> vendorReport = reportGenerator.generateReport(amountOverrides);
        Map<UUID, VendorSettlementLine> vendorByTransferId = vendorReport.stream()
                .collect(Collectors.toMap(VendorSettlementLine::getTransferId, line -> line));

        List<ReconException> exceptions = new java.util.ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();

        for (Transfer transfer : settledTransfers) {
            VendorSettlementLine vendorLine = vendorByTransferId.get(transfer.getId());

            if (vendorLine == null) {
                exceptions.add(ReconException.builder()
                        .transferId(transfer.getId())
                        .exceptionType("MISSING_IN_VENDOR_REPORT")
                        .expectedAmount(transfer.getAmount())
                        .actualAmount(null)
                        .details("Transfer is SETTLED in our system but absent from vendor report")
                        .status("OPEN")
                        .detectedAt(now)
                        .build());
                continue;
            }

            if (!transfer.getAmount().equals(vendorLine.getSettledAmount())) {
                exceptions.add(ReconException.builder()
                        .transferId(transfer.getId())
                        .exceptionType("AMOUNT_MISMATCH")
                        .expectedAmount(transfer.getAmount())
                        .actualAmount(vendorLine.getSettledAmount())
                        .details("Our amount=" + transfer.getAmount()
                                + " but vendor reported=" + vendorLine.getSettledAmount())
                        .status("OPEN")
                        .detectedAt(now)
                        .build());
            }
        }

        if (!exceptions.isEmpty()) {
            reconExceptionRepository.saveAll(exceptions);
            log.warn("Reconciliation found {} exception(s)", exceptions.size());
        } else {
            log.info("Reconciliation complete — no exceptions found ({} transfers checked)", settledTransfers.size());
        }

        return exceptions;
    }
}
package com.moneytransfer.orchestration_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneytransfer.orchestration_service.client.ApnPayOutClient;
import com.moneytransfer.orchestration_service.client.ApolloPayInClient;
import com.moneytransfer.orchestration_service.client.RiskScreeningClient;
import com.moneytransfer.orchestration_service.dto.PayOutRequest;
import com.moneytransfer.orchestration_service.dto.PayOutResult;
import com.moneytransfer.orchestration_service.dto.ScreeningRequest;
import com.moneytransfer.orchestration_service.dto.ScreeningResult;
import com.moneytransfer.orchestration_service.entity.OutboxEvent;
import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.entity.TransferStateTransition;
import com.moneytransfer.orchestration_service.exception.IllegalStateTransitionException;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerClient;
import com.moneytransfer.orchestration_service.repo.OutboxEventRepository;
import com.moneytransfer.orchestration_service.repo.TransferRepository;
import com.moneytransfer.orchestration_service.repo.TransferStateTransitionRepository;
import com.moneytransfer.orchestration_service.review.ReviewQueueEntry;
import com.moneytransfer.orchestration_service.review.ReviewQueueRepository;
import com.moneytransfer.orchestration_service.statemachine.TransferState;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferOrchestrationService {

    private final TransferRepository transferRepository;
    private final TransferStateTransitionRepository transitionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final LedgerClient ledgerClient;
    private final RiskScreeningClient riskScreeningClient;
    private final ReviewQueueRepository reviewQueueRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApolloPayInClient apolloPayInClient;
    private final MeterRegistry meterRegistry;
    
    private final ApnPayOutClient apnPayOutClient;
    @Transactional
    public Transfer initiateTransfer(String idempotencyKey, Long amount, String currency,
                                      UUID sourceAccountId, UUID destAccountId,String payoutMode) {

        var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            MDC.put("correlationId", existing.get().getCorrelationId());
            return existing.get();
        }

        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        OffsetDateTime now = OffsetDateTime.now();
   
        Transfer transfer = Transfer.builder()
                .idempotencyKey(idempotencyKey)
                .correlationId(correlationId)
                .currentState(TransferState.INITIATED)
                .amount(amount)
                .currency(currency)
                .sourceAccountId(sourceAccountId)
                .destAccountId(destAccountId)
                .payoutMode(payoutMode)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Transfer saved = transferRepository.save(transfer);

        recordTransitionAndOutbox(saved, null, TransferState.INITIATED, "SYSTEM", null, now);

        return saved;
    }

    @Transactional
    public Transfer transitionTo(UUID transferId, TransferState targetState, String triggeredBy, String reason) {

        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalStateTransitionException("No transfer found with id=" + transferId));
        
        MDC.put("correlationId", transfer.getCorrelationId());
        
        TransferState currentState = transfer.getCurrentState();

        if (!currentState.canTransitionTo(targetState)) {
            throw new IllegalStateTransitionException(
                    "Illegal transition for transfer=" + transferId + ": " + currentState + " -> " + targetState);
        }
        if (targetState == TransferState.PAY_IN) {
        	 callApolloForPayIn(transfer, targetState);
            callLedgerForPayIn(transfer, targetState);
        }

        OffsetDateTime now = OffsetDateTime.now();

        transfer.setCurrentState(targetState);
        transfer.setUpdatedAt(now);
        Transfer saved = transferRepository.save(transfer);
        if(targetState==TransferState.SETTLED) {
        	meterRegistry.counter("transfer.outcome", "result","success").increment();
        }else if(targetState == TransferState.FAILED) {
        	meterRegistry.counter("transfer.outcome", "result", "failed").increment();
        }
        recordTransitionAndOutbox(saved, currentState, targetState, triggeredBy, reason, now);

        return saved;
    }

    private void callApolloForPayIn(Transfer transfer, TransferState targetState) {
    	String idempotencyKey = transfer.getId() + "-" + targetState.name();
    	com.moneytransfer.orchestration_service.dto.PayInRequest request =com.moneytransfer.orchestration_service.dto.PayInRequest.builder()
                .transferId(transfer.getId())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .idempotencyKey(idempotencyKey)
                .build();
    	apolloPayInClient.payIn(request);

	}

	@Transactional
    public Transfer runScreening(UUID transferId) {

        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalStateTransitionException("No transfer found with id=" + transferId));
        MDC.put("correlationId", transfer.getCorrelationId());
        Transfer screening = transitionTo(transferId, TransferState.SCREENING, "SYSTEM", null);

        ScreeningRequest request = ScreeningRequest.builder()
                .transferId(screening.getId())
                .amount(screening.getAmount())
                .currency(screening.getCurrency())
                .sourceAccountId(screening.getSourceAccountId())
                .destAccountId(screening.getDestAccountId())
                .build();

        ScreeningResult result = riskScreeningClient.screen(request);
        meterRegistry.counter("screening.decision", "decision", result.getDecision()).increment();
        if ("APPROVE".equals(result.getDecision())) {
            return transitionTo(transferId, TransferState.PAY_IN, "RISK_SCREENING",
                    "Auto-approved, riskScore=" + result.getRiskScore());
        } else {
            Transfer parked = transitionTo(transferId, TransferState.PENDING_REVIEW, "RISK_SCREENING",
                    "Routed to manual review, riskScore=" + result.getRiskScore() + ", reason=" + result.getReason());

            ReviewQueueEntry entry = ReviewQueueEntry.builder()
                    .transferId(parked.getId())
                    .riskScore(result.getRiskScore())
                    .reason(result.getReason())
                    .status("PENDING")
                    .createdAt(OffsetDateTime.now())
                    .build();

            reviewQueueRepository.save(entry);

            return parked;
        }
    }

    private void callLedgerForPayIn(Transfer transfer, TransferState targetState) {
        String ledgerIdempotencyKey = transfer.getId() + "-" + targetState.name();

        ledgerClient.postTransaction(
                ledgerIdempotencyKey,
                "TRANSFER_PAY_IN",
                transfer.getCurrency(),
                transfer.getSourceAccountId(),
                transfer.getDestAccountId(),
                transfer.getAmount()
        );
    }

    private void recordTransitionAndOutbox(Transfer transfer, TransferState fromState, TransferState toState,
                                            String triggeredBy, String reason, OffsetDateTime now) {

        TransferStateTransition transition = TransferStateTransition.builder()
                .transferId(transfer.getId())
                .fromState(fromState)
                .toState(toState)
                .triggeredBy(triggeredBy)
                .reason(reason)
                .createdAt(now)
                .build();

        transitionRepository.save(transition);

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("transferId", transfer.getId().toString());
        eventPayload.put("idempotencyKey", transfer.getIdempotencyKey());
        eventPayload.put("fromState", fromState == null ? null : fromState.name());
        eventPayload.put("toState", toState.name());
        eventPayload.put("triggeredBy", triggeredBy);
        eventPayload.put("timestamp", now.toString());
        eventPayload.put("correlationId", transfer.getCorrelationId());

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(eventPayload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("TRANSFER")
                .aggregateId(transfer.getId())
                .correlationId(transfer.getCorrelationId())
                .eventType("TRANSFER_STATE_CHANGED")
                .payload(payloadJson)
                .status("PENDING")
                .createdAt(now)
                .build();

        outboxEventRepository.save(outboxEvent);
    }
    
    @Transactional
    public Transfer runPayOut(UUID transferId) {
    	 Transfer transfer = transferRepository.findById(transferId)
                 .orElseThrow(() -> new IllegalStateTransitionException("No transfer found with id=" + transferId));
    	 MDC.put("correlationId", transfer.getCorrelationId());
    Transfer payingOut = transitionTo(transferId, TransferState.PAY_OUT, "SYSTEM", null);
    String idempotencyKey = payingOut.getId() + "-PAY_OUT";
    
    PayOutRequest request = PayOutRequest.builder()
                    .transferId(payingOut.getId())
                    .amount(payingOut.getAmount())
                    .currency(payingOut.getCurrency())
                    .idempotencyKey(idempotencyKey)
                    .payoutMode(payingOut.getPayoutMode())
                    .build();
    PayOutResult result = apnPayOutClient.payOut(request);
    
    
    if("CASH".equals(payingOut.getPayoutMode())) {
    	return transitionTo(transferId, TransferState.AWAITING_PICKUP, "APN_PAYOUT", " Pickup code generated: " + result.getPickupCode());
    }else {
    	callLedgerForPayOut(payingOut, idempotencyKey);
    	return transitionTo(transferId, TransferState.SETTLED, "APN_PAYOUT",
                "APN reference: " + result.getApnReferenceId());
    }
    }
    
    @Transactional
    public Transfer confirmPickup(UUID transferId,String confirmedBy) {
    	Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalStateTransitionException("No transfer found with id=" + transferId));   
    	MDC.put("correlationId", transfer.getCorrelationId());
    	String idempotencyKey = transfer.getId() + "-PAY_OUT";
    	callLedgerForPayOut(transfer, idempotencyKey);
    	return transitionTo(transferId, TransferState.SETTLED, confirmedBy, "Cash pickup confirmed");
    }
    
    
    private void callLedgerForPayOut(Transfer transfer, String idempotencyKey) {
    	ledgerClient.postTransaction(idempotencyKey, "TRANSFER_PAY_OUT", transfer.getCurrency(),transfer.getSourceAccountId(), transfer.getDestAccountId(),transfer.getAmount());
    }
    
}

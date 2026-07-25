package com.moneytransfer.orchestration_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneytransfer.orchestration_service.entity.OutboxEvent;
import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.entity.TransferStateTransition;
import com.moneytransfer.orchestration_service.exception.IllegalStateTransitionException;
import com.moneytransfer.orchestration_service.ledgerclient.LedgerClient;
import com.moneytransfer.orchestration_service.repo.OutboxEventRepository;
import com.moneytransfer.orchestration_service.repo.TransferRepository;
import com.moneytransfer.orchestration_service.repo.TransferStateTransitionRepository;
import com.moneytransfer.orchestration_service.statemachine.TransferState;

import lombok.RequiredArgsConstructor;
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
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Transactional
    public Transfer initiateTransfer(String idempotencyKey, Long amount, String currency,
                                      UUID sourceAccountId, UUID destAccountId) {

        var existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        OffsetDateTime now = OffsetDateTime.now();

        Transfer transfer = Transfer.builder()
                .idempotencyKey(idempotencyKey)
                .currentState(TransferState.INITIATED)
                .amount(amount)
                .currency(currency)
                .sourceAccountId(sourceAccountId)
                .destAccountId(destAccountId)
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

        TransferState currentState = transfer.getCurrentState();

        if (!currentState.canTransitionTo(targetState)) {
            throw new IllegalStateTransitionException(
                    "Illegal transition for transfer=" + transferId + ": " + currentState + " -> " + targetState);
        }
        if (targetState == TransferState.PAY_IN) {
            callLedgerForPayIn(transfer, targetState);
        }
        OffsetDateTime now = OffsetDateTime.now();

        transfer.setCurrentState(targetState);
        transfer.setUpdatedAt(now);
        Transfer saved = transferRepository.save(transfer);

        recordTransitionAndOutbox(saved, currentState, targetState, triggeredBy, reason, now);

        return saved;
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

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(eventPayload);
        } catch (Exception e) {
                        throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .aggregateType("TRANSFER")
                .aggregateId(transfer.getId())
                .eventType("TRANSFER_STATE_CHANGED")
                .payload(payloadJson)
                .status("PENDING")
                .createdAt(now)
                .build();

        outboxEventRepository.save(outboxEvent);
    }
}
package com.moneytransfer.orchestration_service.controller;

import java.util.UUID;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moneytransfer.orchestration_service.dto.InitiateTransferRequest;
import com.moneytransfer.orchestration_service.dto.TransitionRequest;
import com.moneytransfer.orchestration_service.entity.Transfer;
import com.moneytransfer.orchestration_service.service.TransferOrchestrationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

	private final TransferOrchestrationService transferOrchestrationService;
	
	@PostMapping("/initiate")
    public ResponseEntity<Transfer> initiate(@RequestBody InitiateTransferRequest request) {
        Transfer transfer = transferOrchestrationService.initiateTransfer(
                request.getIdempotencyKey(),
                request.getAmount(),
                request.getCurrency(),
                request.getSourceAccountId(),
                request.getDestAccountId(),
                request.getPayoutMode()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(transfer);
    }
	
	@PostMapping("/{transferId}/transition")
    public ResponseEntity<Transfer> transition(@PathVariable UUID transferId,
                                                @RequestBody TransitionRequest request) {
        Transfer transfer = transferOrchestrationService.transitionTo(
                transferId,
                request.getTargetState(),
                request.getTriggeredBy(),
                request.getReason()
        );
        return ResponseEntity.ok(transfer);
    }
	
	
    @PostMapping("/{transferId}/screen")
    public ResponseEntity<Transfer> screen(@PathVariable UUID transferId) {
        Transfer transfer = transferOrchestrationService.runScreening(transferId);
        return ResponseEntity.ok(transfer);
    }
    @PostMapping("/{transferId}/payout")
    public ResponseEntity<Transfer> payout(@PathVariable UUID transferId) {
    	Transfer transfer=transferOrchestrationService.runPayOut(transferId);
    	return ResponseEntity.ok(transfer);
    }
    
    @PostMapping("/{transferId}/confirm-pickup")
    public ResponseEntity<Transfer> confirmPickup(@PathVariable UUID transferId,
            @RequestParam(defaultValue = "AGENT") String confirmedBy) {
    	 Transfer transfer = transferOrchestrationService.confirmPickup(transferId, confirmedBy);
    	 return ResponseEntity.ok(transfer);
    }
}

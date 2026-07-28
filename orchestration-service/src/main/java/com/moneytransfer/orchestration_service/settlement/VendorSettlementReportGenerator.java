package com.moneytransfer.orchestration_service.settlement;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.moneytransfer.orchestration_service.repo.TransferRepository;
import com.moneytransfer.orchestration_service.statemachine.TransferState;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VendorSettlementReportGenerator {
	 private final TransferRepository transferRepository;
	 
	 public List<VendorSettlementLine> generateReport(){
		 return generateReport(Map.of());
	 }
	 
	 public List<VendorSettlementLine> generateReport(Map<UUID, Long> amountOverrides) {
		 return transferRepository.findAll().stream()
	                .filter(t -> t.getCurrentState() == TransferState.SETTLED)
	                .map(t -> VendorSettlementLine.builder()
	                        .transferId(t.getId())
	                        .settledAmount(amountOverrides.getOrDefault(t.getId(), t.getAmount()))
	                        .vendorReferenceId("vendor_ref_" + t.getId())
	                        .build())
	                .collect(Collectors.toList());
	 }
}

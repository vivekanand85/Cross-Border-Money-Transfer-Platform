package com.moneytransfer.orchestration_service.settlement;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/recon")
@RequiredArgsConstructor
public class ReconController {
	
	private final ReconciliationService reconciliationService;
    private final ReconExceptionRepository reconExceptionRepository;
	
    @PostMapping("/run")
    public ResponseEntity<List<ReconException>> run() {
        return ResponseEntity.ok(reconciliationService.runReconciliation());
    }
    
    @GetMapping("/exceptions")
    public ResponseEntity<List<ReconException>> openExceptions() {
        return ResponseEntity.ok(reconExceptionRepository.findByStatus("OPEN"));
    }
}

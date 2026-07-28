package com.moneytransfer.orchestration_service.settlement;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationScheduler {

	private final ReconciliationService reconciliationService;
	
	@Scheduled(cron="0 0 2 * * *")
	public void runDailyReconciliation() {
		log.info("Starting scheduled daily reconciliation");
        reconciliationService.runReconciliation();
	}
}

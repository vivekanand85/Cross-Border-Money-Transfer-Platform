package com.moneytransfer.orchestration_service.client;

import com.moneytransfer.orchestration_service.dto.ScreeningRequest;
import com.moneytransfer.orchestration_service.dto.ScreeningResult;

public interface RiskScreeningClient {
	ScreeningResult screen(ScreeningRequest request);
	
}

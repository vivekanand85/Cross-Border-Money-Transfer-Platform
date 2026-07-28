package com.moneytransfer.orchestration_service.client;

import com.moneytransfer.orchestration_service.dto.PayOutRequest;
import com.moneytransfer.orchestration_service.dto.PayOutResult;

public interface ApnPayOutClient {
	PayOutResult payOut(PayOutRequest request);
}

package com.moneytransfer.orchestration_service.client;

import com.moneytransfer.orchestration_service.dto.PayInRequest;
import com.moneytransfer.orchestration_service.dto.PayInResult;

public interface ApolloPayInClient {

	PayInResult payIn(PayInRequest request);
}

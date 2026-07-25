package com.moneytransfer.ledger_service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostTransactionRequest {

	    private String idempotencyKey;
	    private String transactionType;
	    private String currency;
	    private List<LedgerEntryRequest> entries;
}

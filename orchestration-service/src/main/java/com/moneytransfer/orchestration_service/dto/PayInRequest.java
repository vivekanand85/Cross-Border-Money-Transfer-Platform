package com.moneytransfer.orchestration_service.dto;

import java.util.UUID;

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
public class PayInRequest {
	private UUID transferId;
    private Long amount;     // minor units, same convention as Ledger
    private String currency;
    private String idempotencyKey;
}

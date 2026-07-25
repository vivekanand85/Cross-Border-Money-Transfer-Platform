package com.moneytransfer.orchestration_service.ledgerclient;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionResponse {
    private UUID id;
    private String idempotencyKey;
    private String transactionType;
    private String currency;
    private String status;
}
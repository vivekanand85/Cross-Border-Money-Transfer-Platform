package com.moneytransfer.orchestration_service.ledgerclient;

import lombok.*;

import java.util.List;
import java.util.UUID;

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

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerEntryRequest {
        private UUID accountId;
        private String entryType; 
        private Long amount;
    }
}
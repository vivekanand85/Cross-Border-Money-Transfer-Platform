package com.moneytransfer.orchestration_service.ledgerclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.UUID;

@Component
public class LedgerClient {

    private final WebClient webClient;

    public LedgerClient(WebClient.Builder webClientBuilder,
                         @Value("${ledger-service.base-url}") String ledgerBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(ledgerBaseUrl).build();
    }
    public LedgerTransactionResponse postTransaction(String idempotencyKey,
                                                       String transactionType,
                                                       String currency,
                                                       UUID debitAccountId,
                                                       UUID creditAccountId,
                                                       Long amount) {

        PostTransactionRequest request = PostTransactionRequest.builder()
                .idempotencyKey(idempotencyKey)
                .transactionType(transactionType)
                .currency(currency)
                .entries(List.of(
                        PostTransactionRequest.LedgerEntryRequest.builder()
                                .accountId(debitAccountId)
                                .entryType("DEBIT")
                                .amount(amount)
                                .build(),
                        PostTransactionRequest.LedgerEntryRequest.builder()
                                .accountId(creditAccountId)
                                .entryType("CREDIT")
                                .amount(amount)
                                .build()
                ))
                .build();

        try {
            return webClient.post()
                    .uri("/api/v1/transactions/post")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(LedgerTransactionResponse.class)
                    .block(); 
        } catch (WebClientResponseException e) {
            throw new LedgerClientException(
                    "Ledger call failed with status " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new LedgerClientException("Ledger call failed: " + e.getMessage(), e);
        }
    }
}
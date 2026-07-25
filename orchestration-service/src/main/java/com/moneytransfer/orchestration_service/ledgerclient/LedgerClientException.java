package com.moneytransfer.orchestration_service.ledgerclient;

public class LedgerClientException extends RuntimeException {

    public LedgerClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
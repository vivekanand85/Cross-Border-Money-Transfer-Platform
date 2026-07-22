package com.moneytransfer.ledger_service.exception;

public class UnbalancedTransactionException extends RuntimeException {

	public UnbalancedTransactionException(String message) {
		super(message);
	}
}

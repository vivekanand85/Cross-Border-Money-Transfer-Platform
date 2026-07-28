package com.moneytransfer.orchestration_service.client;

public class PayOutClientException extends RuntimeException{

	public PayOutClientException(String message,Throwable cause) {
		super(message,cause);
	}
}

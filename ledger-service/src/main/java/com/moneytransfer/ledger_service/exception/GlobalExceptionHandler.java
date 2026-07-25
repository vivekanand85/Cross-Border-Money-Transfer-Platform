package com.moneytransfer.ledger_service.exception;

import java.time.OffsetDateTime;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GlobalExceptionHandler {
	
	@ExceptionHandler(UnbalancedTransactionException.class)
	public ResponseEntity<Map<String,Object>> handleUnbalanced(UnbalancedTransactionException ex){
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(ex.getMessage()));
	}
	@ExceptionHandler(CurrencyMismatchException.class)
	public ResponseEntity<Map<String,Object>>handleCurrencyMisMatch(CurrencyMismatchException ex){
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(ex.getMessage()));
	}
	
	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException ex) {
	    return ResponseEntity.status(HttpStatus.CONFLICT).body(body(ex.getMessage()));
	}
	private Map<String,Object>body(String message){
		Map<String,Object>map=new LinkedHashMap<>();
		map.put("timesta,p", OffsetDateTime.now());
		map.put("status", HttpStatus.BAD_REQUEST.value());
		map.put("error", message);
		return map;
	}
}


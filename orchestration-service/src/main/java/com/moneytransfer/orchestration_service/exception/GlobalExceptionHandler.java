package com.moneytransfer.orchestration_service.exception;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	
	@ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalTransition(IllegalStateTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(ex.getMessage()));
    }
	
	 private Map<String, Object> body(String message) {
	        Map<String, Object> map = new LinkedHashMap<>();
	        map.put("timestamp", OffsetDateTime.now());
	        map.put("status", HttpStatus.CONFLICT.value());
	        map.put("error", message);
	        return map;
	    }

}

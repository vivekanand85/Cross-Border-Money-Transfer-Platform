package com.moneytransfer.ledger_service.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.moneytransfer.ledger_service.dto.PostTransactionRequest;
import com.moneytransfer.ledger_service.entity.Transaction;
import com.moneytransfer.ledger_service.service.TransactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {
	private final TransactionService transactionService;
	
	@PostMapping("/post")
	public ResponseEntity<Transaction>postTransaction(@RequestBody PostTransactionRequest request){
		Transaction result=transactionService.postTransaction(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(result);
		
	}
}

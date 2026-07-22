package com.moneytransfer.ledger_service.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.moneytransfer.ledger_service.entity.LedgerEntry;


public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

	List<LedgerEntry> findByTransactionId(UUID transactionId);
	List<LedgerEntry> findByAccountId(UUID accountId);
	
}

package com.moneytransfer.ledger_service.service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moneytransfer.ledger_service.dto.LedgerEntryRequest;
import com.moneytransfer.ledger_service.dto.PostTransactionRequest;
import com.moneytransfer.ledger_service.entity.LedgerEntry;
import com.moneytransfer.ledger_service.entity.Transaction;
import com.moneytransfer.ledger_service.exception.CurrencyMismatchException;
import com.moneytransfer.ledger_service.exception.IdempotencyConflictException;
import com.moneytransfer.ledger_service.exception.UnbalancedTransactionException;
import com.moneytransfer.ledger_service.repo.LedgerEntryRepository;
import com.moneytransfer.ledger_service.repo.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public Transaction postTransaction(PostTransactionRequest request) {

        var existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            Transaction existingTransaction = existing.get();
            validateSameRequest(existingTransaction, request);
            return existingTransaction; // true retry of the same request — return cached result
        }

        List<LedgerEntryRequest> entries = request.getEntries();

        if (entries == null || entries.size() < 2) {
            throw new UnbalancedTransactionException(
                    "A transaction requires at least two ledger entries (one debit, one credit).");
        }

        long debitTotal = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
                .mapToLong(LedgerEntryRequest::getAmount)
                .sum();

        long creditTotal = entries.stream()
                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)
                .mapToLong(LedgerEntryRequest::getAmount)
                .sum();

        if (debitTotal != creditTotal) {
            throw new UnbalancedTransactionException(
                    "Debits (" + debitTotal + ") do not equal credits (" + creditTotal
                            + ") for idempotencyKey=" + request.getIdempotencyKey());
        }

        boolean hasNonPositiveAmount = entries.stream()
                .anyMatch(e -> e.getAmount() == null || e.getAmount() <= 0);
        if (hasNonPositiveAmount) {
            throw new UnbalancedTransactionException("All ledger entry amounts must be strictly positive.");
        }

        if (request.getCurrency() == null || request.getCurrency().length() != 3) {
            throw new CurrencyMismatchException("Transaction currency must be a valid 3-letter ISO code.");
        }

        OffsetDateTime now = OffsetDateTime.now();

        Transaction transaction = Transaction.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .transactionType(request.getTransactionType())
                .currency(request.getCurrency())
                .status("POSTED")
                .createdAt(now)
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        List<LedgerEntry> ledgerEntries = entries.stream()
                .map(e -> LedgerEntry.builder()
                        .transactionId(savedTransaction.getId())
                        .accountId(e.getAccountId())
                        .entryType(e.getEntryType())
                        .amount(e.getAmount())
                        .currency(request.getCurrency())
                        .createdAt(now)
                        .build())
                .collect(Collectors.toList());

        ledgerEntryRepository.saveAll(ledgerEntries);

        return savedTransaction;
    }

    /**
     * Guards against idempotency-key reuse with a DIFFERENT payload. If a client
     * sends the same idempotencyKey but the transactionType, currency, or the
     * set of ledger entries differ from the original request, that's a client
     * bug (key reused for a different logical transaction) — reject with 409,
     * do NOT silently return the old transaction.
     */
    private void validateSameRequest(Transaction existingTransaction, PostTransactionRequest incoming) {

        boolean typeMatches = Objects.equals(existingTransaction.getTransactionType(), incoming.getTransactionType());
        boolean currencyMatches = Objects.equals(existingTransaction.getCurrency(), incoming.getCurrency());

        if (!typeMatches || !currencyMatches) {
            throw new IdempotencyConflictException(
                    "idempotencyKey=" + incoming.getIdempotencyKey()
                            + " was already used for a different transaction (type/currency mismatch).");
        }

        List<LedgerEntry> existingEntries = ledgerEntryRepository.findByTransactionId(existingTransaction.getId());

        List<LedgerEntryRequest> incomingEntries = incoming.getEntries();
        if (incomingEntries == null || existingEntries.size() != incomingEntries.size()) {
            throw new IdempotencyConflictException(
                    "idempotencyKey=" + incoming.getIdempotencyKey()
                            + " was already used with a different set of ledger entries.");
        }

        // Compare as unordered multisets of (accountId, entryType, amount) —
        // entry order isn't semantically meaningful, so don't require exact order match.
        Set<String> existingSignature = existingEntries.stream()
                .map(e -> e.getAccountId() + "|" + e.getEntryType() + "|" + e.getAmount())
                .collect(Collectors.toSet());

        Set<String> incomingSignature = incomingEntries.stream()
                .map(e -> e.getAccountId() + "|" + e.getEntryType() + "|" + e.getAmount())
                .collect(Collectors.toSet());

        if (!existingSignature.equals(incomingSignature)) {
            throw new IdempotencyConflictException(
                    "idempotencyKey=" + incoming.getIdempotencyKey()
                            + " was already used with different ledger entry details.");
        }
    }
}
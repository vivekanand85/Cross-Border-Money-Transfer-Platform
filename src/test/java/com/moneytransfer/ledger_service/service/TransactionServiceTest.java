package com.moneytransfer.ledger_service.service;

import com.moneytransfer.ledger_service.dto.LedgerEntryRequest;
import com.moneytransfer.ledger_service.dto.PostTransactionRequest;
import com.moneytransfer.ledger_service.entity.LedgerEntry;
import com.moneytransfer.ledger_service.entity.Transaction;
import com.moneytransfer.ledger_service.exception.CurrencyMismatchException;
import com.moneytransfer.ledger_service.exception.IdempotencyConflictException;
import com.moneytransfer.ledger_service.exception.UnbalancedTransactionException;
import com.moneytransfer.ledger_service.repo.LedgerEntryRepository;
import com.moneytransfer.ledger_service.repo.TransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit test — no Spring context, no DB. Repositories are mocked so we're
 * testing TransactionService's own logic in isolation: sum-to-zero validation,
 * positive-amount validation, currency validation, and idempotency behavior
 * (both the "same payload -> return cached" and "different payload -> conflict"
 * paths).
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private TransactionService transactionService;

    private UUID walletAccountId;
    private UUID clearingAccountId;

    @BeforeEach
    void setUp() {
        walletAccountId = UUID.randomUUID();
        clearingAccountId = UUID.randomUUID();
    }

    @Test
    void postTransaction_withBalancedEntries_savesTransactionAndEntries() {
        PostTransactionRequest request = balancedRequest("key-1", 10_000L);

        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    t.setId(UUID.randomUUID());
                    return t;
                });

        Transaction result = transactionService.postTransaction(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("POSTED");
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(ledgerEntryRepository, times(1)).saveAll(anyList());
    }

    @Test
    void postTransaction_withUnbalancedEntries_throwsUnbalancedTransactionException() {
        PostTransactionRequest request = PostTransactionRequest.builder()
                .idempotencyKey("key-2")
                .transactionType("TRANSFER")
                .currency("INR")
                .entries(List.of(
                        LedgerEntryRequest.builder()
                                .accountId(walletAccountId)
                                .entryType(LedgerEntry.EntryType.DEBIT)
                                .amount(10_000L)
                                .build(),
                        LedgerEntryRequest.builder()
                                .accountId(clearingAccountId)
                                .entryType(LedgerEntry.EntryType.CREDIT)
                                .amount(9_999L) // mismatched on purpose
                                .build()
                ))
                .build();

        when(transactionRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.postTransaction(request))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("10000")
                .hasMessageContaining("9999");

        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    @Test
    void postTransaction_withNonPositiveAmount_throwsUnbalancedTransactionException() {
        PostTransactionRequest request = PostTransactionRequest.builder()
                .idempotencyKey("key-3")
                .transactionType("TRANSFER")
                .currency("INR")
                .entries(List.of(
                        LedgerEntryRequest.builder()
                                .accountId(walletAccountId)
                                .entryType(LedgerEntry.EntryType.DEBIT)
                                .amount(0L) // invalid — must be strictly positive
                                .build(),
                        LedgerEntryRequest.builder()
                                .accountId(clearingAccountId)
                                .entryType(LedgerEntry.EntryType.CREDIT)
                                .amount(0L)
                                .build()
                ))
                .build();

        when(transactionRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.postTransaction(request))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("strictly positive");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void postTransaction_withInvalidCurrency_throwsCurrencyMismatchException() {
        PostTransactionRequest request = balancedRequest("key-4", 10_000L);
        request.setCurrency("US"); // invalid — not 3 letters

        when(transactionRepository.findByIdempotencyKey("key-4")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.postTransaction(request))
                .isInstanceOf(CurrencyMismatchException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void postTransaction_withSameIdempotencyKeyAndSamePayload_returnsExistingTransactionWithoutSavingAgain() {
        PostTransactionRequest request = balancedRequest("key-5", 10_000L);

        Transaction existingTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("key-5")
                .transactionType("TRANSFER")
                .currency("INR")
                .status("POSTED")
                .build();

        List<LedgerEntry> existingEntries = List.of(
                LedgerEntry.builder()
                        .accountId(walletAccountId)
                        .entryType(LedgerEntry.EntryType.DEBIT)
                        .amount(10_000L)
                        .build(),
                LedgerEntry.builder()
                        .accountId(clearingAccountId)
                        .entryType(LedgerEntry.EntryType.CREDIT)
                        .amount(10_000L)
                        .build()
        );

        when(transactionRepository.findByIdempotencyKey("key-5")).thenReturn(Optional.of(existingTransaction));
        when(ledgerEntryRepository.findByTransactionId(existingTransaction.getId())).thenReturn(existingEntries);

        Transaction result = transactionService.postTransaction(request);

        assertThat(result).isEqualTo(existingTransaction);
        verify(transactionRepository, never()).save(any());
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    @Test
    void postTransaction_withSameIdempotencyKeyButDifferentPayload_throwsIdempotencyConflictException() {
        PostTransactionRequest incomingRequest = balancedRequest("key-6", 5_000L); // different amount

        Transaction existingTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("key-6")
                .transactionType("TRANSFER")
                .currency("INR")
                .status("POSTED")
                .build();

        List<LedgerEntry> existingEntries = List.of(
                LedgerEntry.builder()
                        .accountId(walletAccountId)
                        .entryType(LedgerEntry.EntryType.DEBIT)
                        .amount(10_000L) // original amount differs from incoming 5_000L
                        .build(),
                LedgerEntry.builder()
                        .accountId(clearingAccountId)
                        .entryType(LedgerEntry.EntryType.CREDIT)
                        .amount(10_000L)
                        .build()
        );

        when(transactionRepository.findByIdempotencyKey("key-6")).thenReturn(Optional.of(existingTransaction));
        when(ledgerEntryRepository.findByTransactionId(existingTransaction.getId())).thenReturn(existingEntries);

        assertThatThrownBy(() -> transactionService.postTransaction(incomingRequest))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void postTransaction_withFewerThanTwoEntries_throwsUnbalancedTransactionException() {
        PostTransactionRequest request = PostTransactionRequest.builder()
                .idempotencyKey("key-7")
                .transactionType("TRANSFER")
                .currency("INR")
                .entries(List.of(
                        LedgerEntryRequest.builder()
                                .accountId(walletAccountId)
                                .entryType(LedgerEntry.EntryType.DEBIT)
                                .amount(10_000L)
                                .build()
                ))
                .build();

        when(transactionRepository.findByIdempotencyKey("key-7")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.postTransaction(request))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("at least two");
    }

    private PostTransactionRequest balancedRequest(String idempotencyKey, long amount) {
        return PostTransactionRequest.builder()
                .idempotencyKey(idempotencyKey)
                .transactionType("TRANSFER")
                .currency("INR")
                .entries(List.of(
                        LedgerEntryRequest.builder()
                                .accountId(walletAccountId)
                                .entryType(LedgerEntry.EntryType.DEBIT)
                                .amount(amount)
                                .build(),
                        LedgerEntryRequest.builder()
                                .accountId(clearingAccountId)
                                .entryType(LedgerEntry.EntryType.CREDIT)
                                .amount(amount)
                                .build()
                ))
                .build();
    }
}
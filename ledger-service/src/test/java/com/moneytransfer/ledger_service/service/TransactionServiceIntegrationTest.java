package com.moneytransfer.ledger_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.moneytransfer.ledger_service.dto.LedgerEntryRequest;
import com.moneytransfer.ledger_service.dto.PostTransactionRequest;
import com.moneytransfer.ledger_service.entity.Account;
import com.moneytransfer.ledger_service.repo.AccountRepository;
import com.moneytransfer.ledger_service.repo.LedgerEntryRepository;
import com.moneytransfer.ledger_service.entity.LedgerEntry;
import com.moneytransfer.ledger_service.entity.Transaction;
import com.moneytransfer.ledger_service.exception.IdempotencyConflictException;
import com.moneytransfer.ledger_service.exception.UnbalancedTransactionException;

@SpringBootTest
@Testcontainers
public class TransactionServiceIntegrationTest {
	
	static PostgreSQLContainer<?>postgres=new PostgreSQLContainer<>(
			DockerImageName.parse("postgres:16.3"))
			.withDatabaseName("ledger_db")
			.withUsername("ledger_user")
			.withPassword("ledger_pass");
	
	static {
	    postgres.start();
	}
	
	@DynamicPropertySource
	static void overrideDataSourceProperties(DynamicPropertyRegistry  registry) {
		 registry.add("spring.datasource.url", postgres::getJdbcUrl);
	        registry.add("spring.datasource.username", postgres::getUsername);
	        registry.add("spring.datasource.password", postgres::getPassword);
	}
	
	 @Autowired
	    private TransactionService transactionService;
	 
	    @Autowired
	    private AccountRepository accountRepository;
	 
	    @Autowired
	    private LedgerEntryRepository ledgerEntryRepository;
	 
	    private UUID walletAccountId;
	    private UUID clearingAccountId;
	
	    
	    @BeforeEach
	    void setUp() {
	                Account wallet = accountRepository.save(Account.builder()
	                .accountType("USER_WALLET")
	                .currency("INR")
	                .status("ACTIVE")
	                .createdAt(OffsetDateTime.now())
	                .build());
	 
	        Account clearing = accountRepository.save(Account.builder()
	                .accountType("SETTLEMENT_CLEARING")
	                .currency("INR")
	                .status("ACTIVE")
	                .createdAt(OffsetDateTime.now())
	                .build());
	 
	        walletAccountId = wallet.getId();
	        clearingAccountId = clearing.getId();
	    }
	    
	    @Test
	    void postTransaction_persistsTransactionAndTwoLedgerEntries_inRealPostgres() {
	    	PostTransactionRequest request=balancedRequest("itest-key-1",10_000L);
	    	Transaction saved = transactionService.postTransaction(request);
	    	
	    	assertThat(saved.getId()).isNotNull();
	        assertThat(saved.getStatus()).isEqualTo("POSTED");
	    	
	        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(saved.getId());
	        assertThat(entries).hasSize(2);
	        
	        
	        long totalDebits = entries.stream()
	                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
	                .mapToLong(LedgerEntry::getAmount)
	                .sum();
	 
	        long totalCredits = entries.stream()
	                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)
	                .mapToLong(LedgerEntry::getAmount)
	                .sum();
	        
	        
	        assertThat(totalDebits).isEqualTo(totalCredits).isEqualTo(10_000L);
	    }
	    
	    @Test
	    void postTransaction_calledTwiceWithSameKey_doesNotCreateDuplicateRows() {
	        PostTransactionRequest request = balancedRequest("itest-key-2", 5_000L);
	 
	        Transaction first = transactionService.postTransaction(request);
	        Transaction second = transactionService.postTransaction(request); // same key, same payload
	 
	        assertThat(second.getId()).isEqualTo(first.getId());
	 
	        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(first.getId());
	        assertThat(entries).hasSize(2); // still exactly 2, not 4 — proves no duplicate insert
	    }
	    @Test
	    void postTransaction_sameKeyDifferentAmount_throwsConflictAndDoesNotMutateOriginal() {
	        PostTransactionRequest original = balancedRequest("itest-key-3", 5_000L);
	        transactionService.postTransaction(original);
	 
	        PostTransactionRequest conflicting = balancedRequest("itest-key-3", 7_000L); // same key, different amount
	 
	        assertThatThrownBy(() -> transactionService.postTransaction(conflicting))
	                .isInstanceOf(IdempotencyConflictException.class);
	 
	        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(walletAccountId);
	        long matchingOriginal = entries.stream().filter(e -> e.getAmount() == 5_000L).count();
	        assertThat(matchingOriginal).isGreaterThanOrEqualTo(1);
	    }
	    
	    @Test
	    void postTransaction_unbalancedRequest_rejectedByRealDbTooWithNoPartialWrite() {
	        PostTransactionRequest request = PostTransactionRequest.builder()
	                .idempotencyKey("itest-key-4")
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
	                                .amount(9_999L)
	                                .build()
	                ))
	                .build();
	 
	        assertThatThrownBy(() -> transactionService.postTransaction(request))
	                .isInstanceOf(UnbalancedTransactionException.class);
	 
	        // Nothing should have been written — validation fails before any save() call.
	        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountId(walletAccountId);
	        assertThat(entries).noneMatch(e -> e.getAmount() == 10_000L);
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


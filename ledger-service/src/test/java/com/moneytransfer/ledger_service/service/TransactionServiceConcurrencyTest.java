 package com.moneytransfer.ledger_service.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.moneytransfer.ledger_service.dto.LedgerEntryRequest;
import com.moneytransfer.ledger_service.dto.PostTransactionRequest;
import com.moneytransfer.ledger_service.entity.Account;
import com.moneytransfer.ledger_service.repo.AccountRepository;
import com.moneytransfer.ledger_service.repo.LedgerEntryRepository;
import com.moneytransfer.ledger_service.entity.LedgerEntry;

@Testcontainers
@SpringBootTest
public class TransactionServiceConcurrencyTest {

	@Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.3"))
            .withDatabaseName("ledger_db")
            .withUsername("ledger_user")
            .withPassword("ledger_pass");
 
    static {
        postgres.start();
    }
    
    @DynamicPropertySource
    static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
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
    
    private static final int THREAD_POOL_SIZE = 20;
    private static final int TOTAL_TRANSACTIONS = 50;
    private static final long AMOUNT_PER_TRANSACTION = 100L;
    
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
    void fiftyConcurrentTransactions_onSameAccount_produceNoLostWritesAndCorrectDerivedBalance() throws InterruptedException {
    	ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    	CountDownLatch startLatch=new CountDownLatch(1);
    	CountDownLatch doneLatch=new CountDownLatch(TOTAL_TRANSACTIONS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

    	for(int i=0;i<TOTAL_TRANSACTIONS;i++) {
    		 final int taskIndex = i;
    		 executor.submit(()->{
    			 try {
    				 startLatch.await();
    				 
    				 PostTransactionRequest request = PostTransactionRequest.builder()
                             .idempotencyKey("concurrent-" + taskIndex) // unique per task — these are 50 DIFFERENT transactions, not retries
                             .transactionType("TRANSFER")
                             .currency("INR")
                             .entries(List.of(
                                     LedgerEntryRequest.builder()
                                             .accountId(walletAccountId)
                                             .entryType(LedgerEntry.EntryType.DEBIT)
                                             .amount(AMOUNT_PER_TRANSACTION)
                                             .build(),
                                     LedgerEntryRequest.builder()
                                             .accountId(clearingAccountId)
                                             .entryType(LedgerEntry.EntryType.CREDIT)
                                             .amount(AMOUNT_PER_TRANSACTION)
                                             .build()
                             ))
                             .build();
    				 transactionService.postTransaction(request);
                     successCount.incrementAndGet();
    			 }
    			 catch(Exception e) {
    				 failureCount.incrementAndGet();
    				 e.printStackTrace();
    			 }
    			 finally {
    				 doneLatch.countDown();
    			 }
    		 });
    	}
    	
    	startLatch.countDown();
    	boolean completedInTime=doneLatch.await(60, TimeUnit.SECONDS);
    	executor.shutdown();
    	
    	assertThat(completedInTime).as("all 50 tasks should complete within timeout").isTrue();
    	assertThat(failureCount.get()).as("no transaction should fail").isEqualTo(0);
    	assertThat(successCount.get()).isEqualTo(TOTAL_TRANSACTIONS);
    	List<LedgerEntry> walletEntries = ledgerEntryRepository.findByAccountId(walletAccountId);
        long debitEntryCount = walletEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
                .count();
        assertThat(debitEntryCount).isEqualTo(TOTAL_TRANSACTIONS);
 
        // 2. Derived balance is exactly correct — sum of all debits on the wallet.
        long totalDebited = walletEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.DEBIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
        assertThat(totalDebited).isEqualTo(TOTAL_TRANSACTIONS * AMOUNT_PER_TRANSACTION);
 
        // 3. Sanity check the other side too — clearing account received exactly as many credits.
        List<LedgerEntry> clearingEntries = ledgerEntryRepository.findByAccountId(clearingAccountId);
        long totalCredited = clearingEntries.stream()
                .filter(e -> e.getEntryType() == LedgerEntry.EntryType.CREDIT)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
        assertThat(totalCredited).isEqualTo(totalDebited); // system-wide double-entry balance holds even under concurrency
    }
}

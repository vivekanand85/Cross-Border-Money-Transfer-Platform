package com.moneytransfer.ledger_service.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

	@Id
	@GeneratedValue
	private UUID id;
	
	@Column(name = "transaction_id", nullable = false)
	private UUID transactionId;
	@Column(name = "account_id", nullable = false)
	private UUID accountId;
	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false, length = 6)
	private EntryType entryType;
	@Column(name = "amount", nullable = false)
    private Long amount;
 
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
 
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    
	public enum EntryType{
		DEBIT, CREDIT
	}
	
}

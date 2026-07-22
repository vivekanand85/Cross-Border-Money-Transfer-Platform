package com.moneytransfer.ledger_service.dto;

import java.util.UUID;

import com.moneytransfer.ledger_service.entity.LedgerEntry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryRequest {

	private UUID accountId;
	private LedgerEntry.EntryType entryType;
	private Long amount;
}

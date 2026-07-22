package com.moneytransfer.ledger_service.entity;

import java.time.OffsetDateTime;
import java.util.*;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

	@Id
	@GeneratedValue
	private UUID id;
	
	  @Column(name = "account_type", nullable = false, length = 50)
	    private String accountType;
	 
	    @Column(name = "currency", nullable = false, length = 3)
	    private String currency;
	 
	    @Column(name = "status", nullable = false, length = 20)
	    private String status;
	 
	    @Column(name = "created_at", nullable = false, updatable = false)
	    private OffsetDateTime createdAt;
	
}

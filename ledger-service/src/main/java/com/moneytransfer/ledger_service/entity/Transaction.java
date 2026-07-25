package com.moneytransfer.ledger_service.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
 
    @Id
    @GeneratedValue
    private UUID id;
 
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;
 
    @Column(name = "transaction_type", nullable = false, length = 50)
    private String transactionType;
 
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
 
    @Column(name = "status", nullable = false, length = 20)
    private String status;
 
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    
    
}   
package com.moneytransfer.orchestration_service.entity;

import com.moneytransfer.orchestration_service.statemachine.TransferState;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transfer_state_transitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferStateTransition {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30)
    private TransferState fromState; // nullable — null for the very first INITIATED row

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 30)
    private TransferState toState;

    @Column(name = "triggered_by", nullable = false, length = 50)
    private String triggeredBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
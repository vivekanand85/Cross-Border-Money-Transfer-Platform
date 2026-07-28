package com.moneytransfer.orchestration_service.settlement;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.SqlTypedJdbcType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="settled_transfers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SettledTransfer {

	@Id
	@GeneratedValue
	private UUID id;
	@Column(name="transfer_id", nullable = false)
	private UUID transferId;
	@Column(name="to_state",nullable=false,length=30)
	private String toState;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
	private String eventPayload;
	@Column(name = "consumed_at", nullable = false, updatable = false)
    private OffsetDateTime consumedAt;
}

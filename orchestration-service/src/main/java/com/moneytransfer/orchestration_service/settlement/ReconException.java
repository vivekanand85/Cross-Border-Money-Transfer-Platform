package com.moneytransfer.orchestration_service.settlement;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.annotation.Generated;
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
@Table(name = "recon_exceptions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconException {
	
	@Id 
	@GeneratedValue
	private UUID id;
	@Column(name="transfer_id")
	private UUID transferId;
	@Column(name = "exception_type", nullable = false, length = 50)
	private String exceptionType;
	@Column(name = "expected_amount")
	private Long expectedAmount;
	@Column(name = "actual_amount")
	private Long actualAmount;
	@Column(name = "details", length = 500)
	private String details;
	@Column(name = "status", nullable = false, length = 20)
	private String status;
	 @Column(name = "detected_at", nullable = false, updatable = false)
	private OffsetDateTime detectedAt;
}

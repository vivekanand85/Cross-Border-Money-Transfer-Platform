package com.moneytransfer.orchestration_service.dto;

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
public class PayOutResult {

	private String apnReferenceId;
	private String status;
	private String pickupCode;
}

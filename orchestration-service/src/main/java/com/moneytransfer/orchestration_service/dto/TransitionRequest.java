package com.moneytransfer.orchestration_service.dto;

import com.moneytransfer.orchestration_service.statemachine.TransferState;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransitionRequest {

	private TransferState targetState;
    private String triggeredBy;
    private String reason; 
}

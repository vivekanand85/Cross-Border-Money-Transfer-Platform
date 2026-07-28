package com.moneytransfer.orchestration_service.settlement;

import lombok.*;
import java.util.UUID;
 
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VendorSettlementLine {
    private UUID transferId;
    private Long settledAmount;
    private String vendorReferenceId;
}

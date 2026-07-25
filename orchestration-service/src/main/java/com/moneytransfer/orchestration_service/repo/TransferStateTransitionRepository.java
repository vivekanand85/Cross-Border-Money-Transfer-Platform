package com.moneytransfer.orchestration_service.repo;

import com.moneytransfer.orchestration_service.entity.TransferStateTransition;
import org.springframework.data.jpa.repository.JpaRepository;
 
import java.util.List;
import java.util.UUID;
 
public interface TransferStateTransitionRepository extends JpaRepository<TransferStateTransition, UUID> {
    List<TransferStateTransition> findByTransferIdOrderByCreatedAtAsc(UUID transferId);
}
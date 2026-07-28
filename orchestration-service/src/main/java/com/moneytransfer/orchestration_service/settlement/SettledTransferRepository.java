package com.moneytransfer.orchestration_service.settlement;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettledTransferRepository extends JpaRepository<SettledTransfer,UUID> {

	List<SettledTransfer> findByToState(String toState);
	 boolean existsByTransferIdAndToState(UUID transferId, String toState);
}

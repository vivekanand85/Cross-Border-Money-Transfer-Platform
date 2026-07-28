package com.moneytransfer.orchestration_service.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReconExceptionRepository extends JpaRepository<ReconException, UUID> {
    List<ReconException> findByStatus(String status);
}
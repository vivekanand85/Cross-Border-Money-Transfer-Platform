package com.moneytransfer.orchestration_service.review;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewQueueRepository extends JpaRepository<ReviewQueueEntry, UUID> {
    List<ReviewQueueEntry> findByStatus(String status);
    Optional<ReviewQueueEntry> findByTransferIdAndStatus(UUID transferId, String status);
}

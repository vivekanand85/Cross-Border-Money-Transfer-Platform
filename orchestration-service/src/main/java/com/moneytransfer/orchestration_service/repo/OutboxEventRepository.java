package com.moneytransfer.orchestration_service.repo;

import com.moneytransfer.orchestration_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
 
import java.util.List;
import java.util.UUID;
 
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}
 
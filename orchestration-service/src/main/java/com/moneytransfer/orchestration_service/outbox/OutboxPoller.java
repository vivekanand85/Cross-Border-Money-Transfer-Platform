package com.moneytransfer.orchestration_service.outbox;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.moneytransfer.orchestration_service.entity.OutboxEvent;
import com.moneytransfer.orchestration_service.repo.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutboxEventRepository outboxEventRepository;
    
    @Scheduled(fixedDelay=5000)
    @Transactional
    public void pollAndPublic() {
    	List<OutboxEvent> pendingEvents=outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
    	for (OutboxEvent event : pendingEvents) {
    		 try {
                 publish(event);
                 event.setStatus("PUBLISHED");
                 event.setPublishedAt(OffsetDateTime.now());
                 outboxEventRepository.save(event);
             }
    		 catch (Exception e) {
    			 log.error("Failed to publish outbox event id={}, will retry next cycle", event.getId(), e);
    		 }
    	}
    }
    
    private void publish(OutboxEvent event) {
    	log.info("Publishing outbox event: type={} aggregateId={} payload={}",
    			event.getEventType(),event.getAggregateType(),event.getPayload());
    }
}

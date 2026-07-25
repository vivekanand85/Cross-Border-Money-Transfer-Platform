package com.moneytransfer.orchestration_service.outbox;
 
import com.moneytransfer.orchestration_service.entity.OutboxEvent;
import com.moneytransfer.orchestration_service.repo.OutboxEventRepository;
 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
 
import java.time.OffsetDateTime;
import java.util.List;
 

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
 
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
 
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
 
        for (OutboxEvent event : pendingEvents) {
            try {
                publish(event);
                event.setStatus("PUBLISHED");
                event.setPublishedAt(OffsetDateTime.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={}, will retry next cycle", event.getId(), e);
            }
        }
    }
 
    private void publish(OutboxEvent event) throws Exception {
              kafkaTemplate.send(KafkaTopics.TRANSFER_EVENTS, event.getAggregateId().toString(), event.getPayload())
                .get();
 
        log.info("Published outbox event id={} to topic={} key={}",
                event.getId(), KafkaTopics.TRANSFER_EVENTS, event.getAggregateId());
    }
}
 

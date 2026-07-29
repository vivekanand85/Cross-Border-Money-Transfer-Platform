package com.moneytransfer.orchestration_service.settlement;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.Uuid;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Component
@RequiredArgsConstructor
@Slf4j
public class TransferEventConsumer {

	private final SettledTransferRepository settledTransferRepository;
	private final ObjectMapper objectMapper=new ObjectMapper();
	
	@KafkaListener(topics="transfer-events",groupId= "settlement-consumer")
	@Transactional
	public void onTransferEvent(ConsumerRecord<String, String> record) {
		String payload=record.value();
		
		Header header=record.headers().lastHeader("\"X-Correlation-Id");
	    
		String correlationId = header != null
                ? new String(header.value(), StandardCharsets.UTF_8)
                : "unknown";
		
		try {
			JsonNode json=objectMapper.readTree(payload);
			UUID transferId=UUID.fromString(json.get("transferId").asText());
			String toState=json.get("toState").asText();
			if(settledTransferRepository.existsByTransferIdAndToState(transferId, toState)) {
				log.info("Duplicate event for transferId={} toState={}, skipping(already consumed",
				transferId,toState);
				return;
			}
			
			
			SettledTransfer record2=SettledTransfer.builder()
					.transferId(transferId)
					.toState(toState)
					.eventPayload(payload)
					.consumedAt(OffsetDateTime.now())
					.build();
			
			settledTransferRepository.save(record2);
			
			  log.info("Consumed transfer-events message: transferId={} toState={}", transferId, toState);
		}
		catch(Exception e) {
			log.error("Failed to process transfer-events message, payload={}", payload, e);
		}
		finally {
            MDC.clear();
        }
	}
	
}

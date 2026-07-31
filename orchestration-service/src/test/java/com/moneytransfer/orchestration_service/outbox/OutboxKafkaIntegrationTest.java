package com.moneytransfer.orchestration_service.outbox;

import com.moneytransfer.orchestration_service.entity.OutboxEvent;
import com.moneytransfer.orchestration_service.repo.OutboxEventRepository;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import org.testcontainers.kafka.KafkaContainer;
@SpringBootTest
@Testcontainers
class OutboxKafkaIntegrationTest {
	private static final ObjectMapper objectMapper = new ObjectMapper();
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("orchestration_db")
            .withUsername("orchestration_user")
            .withPassword("orchestration_pass");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
    static {
        postgres.start();
        kafka.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUpConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(KafkaTopics.TRANSFER_EVENTS));
    }

    @AfterEach
    void tearDownConsumer() {
        consumer.close();
    }
    private boolean seeked = false;

    @Test
    void pendingOutboxEvent_isActuallyPublishedToRealKafkaTopic() {

        UUID transferId = UUID.randomUUID();
        String expectedPayload = "{\"transferId\":\"" + transferId + "\",\"toState\":\"SCREENING\"}";

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("TRANSFER")
                .aggregateId(transferId)
                .eventType("TRANSFER_STATE_CHANGED")
                .payload(expectedPayload)
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();

        outboxEventRepository.save(event);

   
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!seeked && !consumer.assignment().isEmpty()) {
                consumer.seekToBeginning(consumer.assignment());
                seeked = true;
            }
            boolean found = false;
            for (ConsumerRecord<String, String> record : records) {
                if (record.key().equals(transferId.toString())) {
                    JsonNode actual = objectMapper.readTree(record.value());
                    found = transferId.toString().equals(actual.get("transferId").asText())
                            && "SCREENING".equals(actual.get("toState").asText());
                }
            }
            assertThat(found).as("expected message not found on topic yet").isTrue();
        });
    }
}
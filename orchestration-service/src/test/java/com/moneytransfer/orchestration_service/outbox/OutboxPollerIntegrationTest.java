package com.moneytransfer.orchestration_service.outbox;

import com.moneytransfer.orchestration_service.entity.OutboxEvent;
import com.moneytransfer.orchestration_service.repo.OutboxEventRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;


@SpringBootTest
@Testcontainers
class OutboxPollerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("orchestration_db")
            .withUsername("orchestration_user")
            .withPassword("orchestration_pass");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void pendingOutboxEvent_isPickedUpAndMarkedPublished_byScheduledPoller() {

        OffsetDateTime now = OffsetDateTime.now();

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("TRANSFER")
                .aggregateId(UUID.randomUUID())
                .eventType("TRANSFER_STATE_CHANGED")
                .payload("{\"test\":\"outbox-integration\"}")
                .status("PENDING")
                .createdAt(now)
                .build();

        OutboxEvent saved = outboxEventRepository.save(event);

     
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    OutboxEvent reloaded = outboxEventRepository.findById(saved.getId()).orElseThrow();
                    assertThat(reloaded.getStatus()).isEqualTo("PUBLISHED");
                    assertThat(reloaded.getPublishedAt()).isNotNull();
                });
    }

    @Test
    void alreadyPublishedEvent_isNotReprocessed() {
        OffsetDateTime now = OffsetDateTime.now();

        OutboxEvent alreadyPublished = OutboxEvent.builder()
                .aggregateType("TRANSFER")
                .aggregateId(UUID.randomUUID())
                .eventType("TRANSFER_STATE_CHANGED")
                .payload("{\"test\":\"already-published\"}")
                .status("PUBLISHED")
                .createdAt(now)
                .publishedAt(now)
                .build();

        OutboxEvent saved = outboxEventRepository.save(alreadyPublished);

        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        assertThat(pending).noneMatch(e -> e.getId().equals(saved.getId()));
    }
}
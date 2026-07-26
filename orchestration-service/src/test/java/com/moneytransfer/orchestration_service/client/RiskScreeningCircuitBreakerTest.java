package com.moneytransfer.orchestration_service.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.moneytransfer.orchestration_service.dto.ScreeningRequest;
import com.moneytransfer.orchestration_service.dto.ScreeningResult;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RiskScreeningCircuitBreakerTest {

    static WireMockServer wireMockServer;

    private static final int FIXED_WIREMOCK_PORT = 18089;

    @BeforeEach
    void setUp() {
        
        wireMockServer = new WireMockServer(FIXED_WIREMOCK_PORT);
        wireMockServer.start();
        configureFor("localhost", FIXED_WIREMOCK_PORT);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {

        registry.add("risk-screening.base-url", () -> "http://localhost:18089");
    }

    @Autowired
    private RiskScreeningClient riskScreeningClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void repeatedVendorFailures_openTheCircuitBreaker_andFallbackReturnsManualReview() {
        stubFor(post(urlEqualTo("/screen"))
                .willReturn(aResponse().withStatus(500).withBody("vendor is down")));

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("riskScreening");

      
        ScreeningRequest request = ScreeningRequest.builder()
                .transferId(UUID.randomUUID())
                .amount(1000L)
                .currency("INR")
                .sourceAccountId(UUID.randomUUID())
                .destAccountId(UUID.randomUUID())
                .build();

        for (int i = 0; i < 6; i++) {
     
            ScreeningResult result = riskScreeningClient.screen(request);
            assertThat(result.getDecision()).isEqualTo("MANUAL_REVIEW");
        }

       
        assertThat(breaker.getState())
                .as("circuit breaker should be OPEN after repeated failures exceeding the configured threshold")
                .isEqualTo(CircuitBreaker.State.OPEN);
    }
}
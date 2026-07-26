package com.moneytransfer.orchestration_service.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.moneytransfer.orchestration_service.dto.ScreeningRequest;
import com.moneytransfer.orchestration_service.dto.ScreeningResult;

public class RiskScreeningClientImplTest {

	private WireMockServer wireMockServer;
	private RiskScreeningClient client;
	
	 @BeforeEach
	    void setUp() {
		 System.out.println("before each wire mock server setting up");
	        wireMockServer = new WireMockServer(0);
	        wireMockServer.start();
	        WireMock.configureFor("localhost", wireMockServer.port());
	        
	        String baseUrl = "http://localhost:" + wireMockServer.port();
			 System.out.println(WebClient.builder()+ " calling riskScreening CLinet URL "+baseUrl);

	        client = new RiskScreeningClientImpl(WebClient.builder(), baseUrl);
	    }
	 @AfterEach
	    void tearDown() {
	        wireMockServer.stop();
	    }
	 @Test
	 void screen_lowRiskResponse_parsesCorrectly() {
		 stubFor(post(urlEqualTo("/screen"))
	                .willReturn(aResponse()
	                        .withStatus(200)
	                        .withHeader("Content-Type", "application/json")
	                        .withBody("""
	                                {
	                                  "riskScore": 12,
	                                  "decision": "APPROVE",
	                                  "reason": "low risk"
	                                }
	                                """)));
		 ScreeningRequest request = ScreeningRequest.builder()
	                .transferId(UUID.randomUUID())
	                .amount(5000L)
	                .currency("INR")
	                .sourceAccountId(UUID.randomUUID())
	                .destAccountId(UUID.randomUUID())
	                .build();
		 
		 ScreeningResult  result=client.screen(request);	
		 System.out.println("result get risk score "+result.getRiskScore()+" "+result.getDecision());
		 assertThat(result.getRiskScore()).isEqualTo(12);
		 assertThat(result.getDecision()).isEqualTo("APPROVE");
	 }
	 @Test
	    void screen_highRiskResponse_parsesCorrectly() {
	        stubFor(post(urlEqualTo("/screen"))
	                .willReturn(aResponse()
	                        .withStatus(200)
	                        .withHeader("Content-Type", "application/json")
	                        .withBody("""
	                                {
	                                  "riskScore": 88,
	                                  "decision": "MANUAL_REVIEW",
	                                  "reason": "amount exceeds threshold"
	                                }
	                                """)));
	 
	        ScreeningRequest request = ScreeningRequest.builder()
	                .transferId(UUID.randomUUID())
	                .amount(500000L)
	                .currency("INR")
	                .sourceAccountId(UUID.randomUUID())
	                .destAccountId(UUID.randomUUID())
	                .build();
	 
	        ScreeningResult result = client.screen(request);
	 
	        assertThat(result.getRiskScore()).isEqualTo(88);
	        assertThat(result.getDecision()).isEqualTo("MANUAL_REVIEW");
	    }
}

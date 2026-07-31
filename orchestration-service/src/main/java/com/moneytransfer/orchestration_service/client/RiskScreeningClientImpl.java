package com.moneytransfer.orchestration_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.moneytransfer.orchestration_service.dto.ScreeningRequest;
import com.moneytransfer.orchestration_service.dto.ScreeningResult;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Component
public class RiskScreeningClientImpl implements RiskScreeningClient {
 
    private final WebClient webClient;
 
    public RiskScreeningClientImpl(WebClient.Builder webClientBuilder,
                                    @Value("${risk-screening.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

	@Override
	@CircuitBreaker(name="riskScreening")
	@Retry(name="riskScreening",fallbackMethod="fallbackScreen")
	public ScreeningResult screen(ScreeningRequest request) {
		try {
			return webClient.post()
					.uri("/screen")
					.bodyValue(request)
					.retrieve()
					.bodyToMono(ScreeningResult.class)
					.block();
		}
		catch (WebClientResponseException e) {
            throw new ScreeningClientException(
                    "Screening vendor returned " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new ScreeningClientException("Screening vendor call failed: " + e.getMessage(), e);
        }
	}
	
	
	@SuppressWarnings("unused")
    private ScreeningResult fallbackScreen(ScreeningRequest request, Throwable t) {
        return ScreeningResult.builder()
                .riskScore(-1) // sentinel: -1 means "could not be scored", not a real 0-100 score
                .decision("MANUAL_REVIEW")
                .reason("Risk screening vendor unavailable: " + t.getMessage())
                .build();
    }
}

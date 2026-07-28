package com.moneytransfer.orchestration_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.moneytransfer.orchestration_service.dto.PayOutRequest;
import com.moneytransfer.orchestration_service.dto.PayOutResult;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Component
public class ApnPayOutClientImpl implements ApnPayOutClient{

	private final WebClient webClient;
	
	public ApnPayOutClientImpl(WebClient.Builder webClientBuilder,
			@Value("${apn.base-url}") String baseUrl
			) {
		this.webClient=webClientBuilder.baseUrl(baseUrl).build();
	}
	
	@Override
	@CircuitBreaker(name="apnPayOut",fallbackMethod="fallback")
	@Retry(name="apnPayOut")
	public PayOutResult payOut(PayOutRequest request) {
		try {
			return webClient.post()
					.uri("/payout")
					.bodyValue(request)
					.retrieve()
					.bodyToMono(PayOutResult.class)
					.block();
		}
		catch (WebClientResponseException e) {
            throw new PayOutClientException(
                    "APN returned " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new PayOutClientException("APN call failed: " + e.getMessage(), e);
        }
	}
	
	@SuppressWarnings("unused")
	private PayOutResult fallback(PayOutRequest request, Throwable t) {
		throw new PayOutClientException("APN unavailable after retries/circuit open: " + t.getMessage(), t);
    }
	
}

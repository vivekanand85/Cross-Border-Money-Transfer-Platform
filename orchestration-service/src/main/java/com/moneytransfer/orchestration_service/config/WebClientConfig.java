package com.moneytransfer.orchestration_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
@Configuration
public class WebClientConfig {

	@Bean
	public WebClient.Builder webClientBuilder(){
		return WebClient.builder().filter((request, next) -> {
            String correlationId = MDC.get("correlationId");
            ClientRequest filtered = ClientRequest.from(request)
                    .header("X-Correlation-Id", correlationId != null ? correlationId : "unknown")
                    .build();
            return next.exchange(filtered);
        });
	}
}

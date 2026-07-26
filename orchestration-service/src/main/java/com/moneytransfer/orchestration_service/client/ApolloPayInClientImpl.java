package com.moneytransfer.orchestration_service.client;

import com.moneytransfer.orchestration_service.dto.PayInRequest;
import com.moneytransfer.orchestration_service.dto.PayInResult;

import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApolloPayInClientImpl implements ApolloPayInClient {

    private final String stripeApiKey;

    public ApolloPayInClientImpl(@Value("${stripe.api-key}") String stripeApiKey) {
        this.stripeApiKey = stripeApiKey;
    }

    @Override
    @CircuitBreaker(name = "apolloPayIn", fallbackMethod = "fallback")
    @Retry(name = "apolloPayIn")
    public PayInResult payIn(PayInRequest request) {
        try {
  RequestOptions options = RequestOptions.builder()
                    .setApiKey(stripeApiKey)
                    .setIdempotencyKey(request.getIdempotencyKey())
                    .build();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(request.getAmount())
                    .setCurrency(request.getCurrency().toLowerCase())
                    .setConfirm(true)
                    .setPaymentMethod("pm_card_visa") // Stripe's built-in test payment method
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build())
                    .build();

            PaymentIntent intent = PaymentIntent.create(params, options);

            return PayInResult.builder()
                    .stripePaymentIntentId(intent.getId())
                    .status(intent.getStatus())
                    .build();

        } catch (Exception e) {
            throw new PayInClientException("Stripe pay-in call failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private PayInResult fallback(PayInRequest request, Throwable t) {
   throw new PayInClientException("Stripe unavailable after retries/circuit open: " + t.getMessage(), t);
    }
}
package com.moneytransfer.ledger_service.config;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class CorrelationIdFilter {
	
	public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain)
			throws IOException, ServletException {
		String correlationId = ((HttpServletRequest) req).getHeader("X-Correlation-Id");
        MDC.put("correlationId", correlationId != null ? correlationId : "unknown");
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
	}
}

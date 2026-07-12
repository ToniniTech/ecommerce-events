package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the RestClient used to call product-service.
 *
 * <p>Timeouts are set explicitly and on purpose: a synchronous inter-service call
 * must never block an order thread indefinitely if product-service is slow or hung.
 * Without them, a stalled downstream can exhaust the Tomcat thread pool of
 * order-service (cascading failure).
 */
@Configuration
public class ProductClientConfig {

    @Bean
    public RestClient productRestClient(RestClient.Builder builder,  // ← inyectado por Spring, ya instrumentado
                                       @Value("${product.service.url}") String baseUrl)  {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000); // ms to establish the TCP connection
        factory.setReadTimeout(3000);    // ms to wait for the response body

        return builder
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}

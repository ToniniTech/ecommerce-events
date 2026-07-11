package com.ecommerce;

import com.ecommerce.client.ProductCatalogClient;
import com.ecommerce.client.ProductInfo;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

// src/test/java/com/ecommerce/IntegrationTestBase.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class IntegrationTestBase {
        @MockBean
        protected ProductCatalogClient productCatalogClient;

    @BeforeEach
    void stubCatalog() {
        // lenient(): OutboxProcessorIntegrationTest never resolves a product.
        lenient().when(productCatalogClient.resolve(anyString()))
                .thenAnswer(inv -> {
                    ProductInfo tecladoMecanico = new ProductInfo(
                            inv.getArgument(0), "Teclado Mecanico", new BigDecimal("129.99"));
                    return tecladoMecanico;
                });

    }

        // MySQL — starts up once for all tests
        @Container
        static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("order_db_test")
                .withUsername("testuser")
                .withPassword("testpass");


        // RabbitMQ with the management plugin enabled
        @Container
        static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");

        // Tells Spring to use the container URLs
        // instead of those in application.yml
        @DynamicPropertySource
        static void overrideProperties(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url",      mysql::getJdbcUrl);
            registry.add("spring.datasource.username", mysql::getUsername);
            registry.add("spring.datasource.password", mysql::getPassword);

            registry.add("spring.rabbitmq.host",       rabbitmq::getHost);
            registry.add("spring.rabbitmq.port",       rabbitmq::getAmqpPort);
            registry.add("spring.rabbitmq.username",   () -> "guest");
            registry.add("spring.rabbitmq.password",   () -> "guest");

            // application.yml requires JWT_SECRET with no fallback, so the context
            // would fail to load here. Tests supply their own throwaway secret.
            registry.add("jwt.secret",
                    () -> "3d8f2c1a9b4e7f0d5c2a8b3e6f1d4c7a0b5e8f2d1c4a7b0e3f6d9c2a5b8e1f4");


        }



}

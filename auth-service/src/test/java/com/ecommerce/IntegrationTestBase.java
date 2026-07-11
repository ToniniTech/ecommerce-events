package com.ecommerce;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class IntegrationTestBase {
    // MySQL — starts up once for all tests
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("auth_db_test")
            .withUsername("testuser")
            .withPassword("testpass")
            .withReuse(true);  // reutiliza el contenedor entre runs

    // RabbitMQ with the management plugin enabled
    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management")
            .withReuse(true);

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
    }
}

package com.ecommerce.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * RabbitMQ topology for Order Service.
 *
 * Exchanges:
 *   - orders.exchange   (topic) → publishes OrderCreated
 *   - payments.exchange (topic) → consumes PaymentProcessed, PaymentFailed
 *
 * Dead Letter setup:
 *   Every queue has a DLX argument pointing to a DLQ.
 *   After max-attempts, unprocessable messages land in the DLQ for manual inspection.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.orders}")
    private String ordersExchange;

    @Value("${rabbitmq.exchange.payments}")
    private String paymentsExchange;

    @Value("${rabbitmq.queue.payment-processed}")
    private String paymentProcessedQueue;

    @Value("${rabbitmq.queue.payment-failed}")
    private String paymentFailedQueue;

    @Value("${rabbitmq.queue.payment-processed-dlq}")
    private String paymentProcessedDlq;

    @Value("${rabbitmq.routing-key.payment-processed}")
    private String paymentProcessedRoutingKey;

    @Value("${rabbitmq.routing-key.payment-failed}")
    private String paymentFailedRoutingKey;

    // ─── Exchanges ────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange ordersExchange() {
        return ExchangeBuilder.topicExchange(ordersExchange).durable(true).build();
    }

    @Bean
    public TopicExchange paymentsExchange() {
        return ExchangeBuilder.topicExchange(paymentsExchange).durable(true).build();
    }

    /** Dead Letter Exchange — all DLQs bind here */
    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange("dlx.exchange").durable(true).build();
    }

    // ─── Queues ───────────────────────────────────────────────────────────────

    @Bean
    public Queue paymentProcessedQueue() {
        return QueueBuilder.durable(paymentProcessedQueue)
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "dlq.payment.processed")
                .withArgument("x-message-ttl", 86400000) // 24h TTL
                .build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(paymentFailedQueue)
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "dlq.payment.failed")
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Queue paymentProcessedDlqQueue() {
        return QueueBuilder.durable(paymentProcessedDlq).build();
    }

    @Bean
    public Queue orderCreatedQueue(){
        return new Queue("order.created.queue", true);
    }

    // ─── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding paymentProcessedBinding() {
        return BindingBuilder
                .bind(paymentProcessedQueue())
                .to(paymentsExchange())
                .with(paymentProcessedRoutingKey);
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder
                .bind(paymentFailedQueue())
                .to(paymentsExchange())
                .with(paymentFailedRoutingKey);
    }

    @Bean
    public Binding paymentProcessedDlqBinding() {
        return BindingBuilder
                .bind(paymentProcessedDlqQueue())
                .to(deadLetterExchange())
                .with("dlq.payment.processed");
    }

    @Bean
    public Binding binding(
            Queue orderCreatedQueue,
            TopicExchange ordersExchange) {

        return BindingBuilder
                .bind(orderCreatedQueue)
                .to(ordersExchange)
                .with("order.created");
    }

    // ─── Message Converter ────────────────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        // Publisher confirms — ensures message reached the broker
        template.setMandatory(true);
        return template;
    }

    // ─── Retry + DLQ recoverer ────────────────────────────────────────────────

    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, "dlx.exchange", "dlq.payment.processed");
    }

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(2000, 2.0, 10000)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setAdviceChain(retryInterceptor());
        return factory;
    }
}

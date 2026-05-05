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
 * RabbitMQ topology for Notification Service.
 *
 * This service is a pure CONSUMER — it never publishes events.
 * It subscribes to ALL business events to send user notifications:
 *   - orders.exchange  → OrderCreated   → order confirmation email
 *   - payments.exchange → PaymentProcessed → payment success email
 *   - payments.exchange → PaymentFailed    → payment failure email
 *
 * Each event type has its own dedicated queue + DLQ for isolation.
 * A failure in one notification type does NOT block others.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.orders}")
    private String ordersExchange;

    @Value("${rabbitmq.exchange.payments}")
    private String paymentsExchange;

    @Value("${rabbitmq.queue.notification-order-created}")
    private String notifOrderCreatedQueue;

    @Value("${rabbitmq.queue.notification-payment-processed}")
    private String notifPaymentProcessedQueue;

    @Value("${rabbitmq.queue.notification-payment-failed}")
    private String notifPaymentFailedQueue;

    @Value("${rabbitmq.queue.notification-order-created-dlq}")
    private String notifOrderCreatedDlq;

    @Value("${rabbitmq.queue.notification-payment-dlq}")
    private String notifPaymentDlq;

    @Value("${rabbitmq.routing-key.order-created}")
    private String orderCreatedKey;

    @Value("${rabbitmq.routing-key.payment-processed}")
    private String paymentProcessedKey;

    @Value("${rabbitmq.routing-key.payment-failed}")
    private String paymentFailedKey;

    // ─── Exchanges ────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange ordersExchange() {
        return ExchangeBuilder.topicExchange(ordersExchange).durable(true).build();
    }

    @Bean
    public TopicExchange paymentsExchange() {
        return ExchangeBuilder.topicExchange(paymentsExchange).durable(true).build();
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return ExchangeBuilder.topicExchange("dlx.exchange").durable(true).build();
    }

    // ─── Queues ───────────────────────────────────────────────────────────────

    @Bean
    public Queue notifOrderCreatedQueue() {
        return QueueBuilder.durable(notifOrderCreatedQueue)
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "dlq.notif.order-created")
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Queue notifPaymentProcessedQueue() {
        return QueueBuilder.durable(notifPaymentProcessedQueue)
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "dlq.notif.payment-processed")
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Queue notifPaymentFailedQueue() {
        return QueueBuilder.durable(notifPaymentFailedQueue)
                .withArgument("x-dead-letter-exchange", "dlx.exchange")
                .withArgument("x-dead-letter-routing-key", "dlq.notif.payment-failed")
                .withArgument("x-message-ttl", 86400000)
                .build();
    }

    @Bean
    public Queue notifOrderCreatedDlqQueue() {
        return QueueBuilder.durable(notifOrderCreatedDlq).build();
    }

    @Bean
    public Queue notifPaymentDlqQueue() {
        return QueueBuilder.durable(notifPaymentDlq).build();
    }

    // ─── Bindings ─────────────────────────────────────────────────────────────

    @Bean
    public Binding notifOrderCreatedBinding() {
        return BindingBuilder
                .bind(notifOrderCreatedQueue())
                .to(ordersExchange())
                .with(orderCreatedKey);
    }

    @Bean
    public Binding notifPaymentProcessedBinding() {
        return BindingBuilder
                .bind(notifPaymentProcessedQueue())
                .to(paymentsExchange())
                .with(paymentProcessedKey);
    }

    @Bean
    public Binding notifPaymentFailedBinding() {
        return BindingBuilder
                .bind(notifPaymentFailedQueue())
                .to(paymentsExchange())
                .with(paymentFailedKey);
    }

    @Bean
    public Binding notifOrderCreatedDlqBinding() {
        return BindingBuilder
                .bind(notifOrderCreatedDlqQueue())
                .to(deadLetterExchange())
                .with("dlq.notif.order-created");
    }

    @Bean
    public Binding notifPaymentDlqBinding() {
        return BindingBuilder
                .bind(notifPaymentDlqQueue())
                .to(deadLetterExchange())
                .with("dlq.notif.payment-#");
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
        return template;
    }

    // ─── Retry + DLQ Recoverer ────────────────────────────────────────────────

    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(
                rabbitTemplate, "dlx.exchange", "dlq.notif.order-created");
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

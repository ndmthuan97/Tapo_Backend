package backend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RabbitMQ configuration — java-pro: event-driven async messaging.
 *
 * <p><b>Profile: {@code self-host}</b> — only active when self-hosted.
 * On Azure App Service, {@code @Async} continues to handle notifications directly.
 * Switch is zero-breaking: no existing {@code @Async} code is modified.
 *
 * <p>Queues:
 * <ul>
 *   <li>{@code order.created}     — new order event (durable)</li>
 *   <li>{@code order.created.dlq} — Dead Letter Queue, TTL 1h before retry</li>
 * </ul>
 *
 * <p>Exchange topology:
 * {@code tapo.events} (topic exchange)
 *   → routing key {@code order.created} → {@code order.created} queue
 *
 * <p>Activate: {@code SPRING_PROFILES_ACTIVE=self-host}
 */
@Configuration
@Profile("self-host")
public class RabbitMqConfig {

    // ── Queue + Exchange names ────────────────────────────────────────────────

    public static final String EXCHANGE         = "tapo.events";
    public static final String QUEUE_ORDER      = "order.created";
    public static final String QUEUE_ORDER_DLQ  = "order.created.dlq";
    public static final String ROUTING_ORDER    = "order.created";

    // ── Queues ────────────────────────────────────────────────────────────────

    @Bean
    Queue orderCreatedQueue() {
        return QueueBuilder.durable(QUEUE_ORDER)
                .withArgument("x-dead-letter-exchange", "")          // default exchange
                .withArgument("x-dead-letter-routing-key", QUEUE_ORDER_DLQ)
                .build();
    }

    /** Dead Letter Queue — holds failed messages for 1h before optional retry. */
    @Bean
    Queue orderCreatedDlq() {
        return QueueBuilder.durable(QUEUE_ORDER_DLQ)
                .withArgument("x-message-ttl", 3_600_000L)  // 1 hour TTL
                .build();
    }

    // ── Exchange ──────────────────────────────────────────────────────────────

    /** Topic exchange — supports routing key patterns (e.g. order.*, *.created). */
    @Bean
    TopicExchange tapoEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE)
                .durable(true)
                .build();
    }

    // ── Bindings ──────────────────────────────────────────────────────────────

    @Bean
    Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange tapoEventsExchange) {
        return BindingBuilder
                .bind(orderCreatedQueue)
                .to(tapoEventsExchange)
                .with(ROUTING_ORDER);
    }

    // ── Template + Serialization ──────────────────────────────────────────────

    /** JSON serialization for messages — java-pro: always use typed messages. */
    @Bean
    MessageConverter jacksonMessageConverter() {
        return new org.springframework.amqp.support.converter.JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }
}

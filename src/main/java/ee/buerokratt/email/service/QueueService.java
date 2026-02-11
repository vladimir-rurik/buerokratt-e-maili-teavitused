package ee.buerokratt.email.service;

import ee.buerokratt.email.model.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for managing email queue operations.
 *
 * Handles publishing email messages to RabbitMQ queues and
 * managing message routing, priorities, and delays.
 */
@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public QueueService(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${rabbitmq.exchange:email.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key:email.notifications}")
    private String routingKey;

    @Value("${rabbitmq.retry.exchange:email.retry.exchange}")
    private String retryExchange;

    @Value("${rabbitmq.retry.routing-key:email.retry}")
    private String retryRoutingKey;

    /**
     * Publish an email message to the main queue.
     *
     * @param message Email message to publish
     */
    public void publishEmail(EmailMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);

            var builder = MessageBuilder.withBody(jsonMessage.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setExpiration(String.valueOf(getMessageTtl(message.getPriority())))
                .setHeader("event_id", message.getEventId())
                .setHeader("event_type", message.getEventType())
                .setHeader("priority", message.getPriority());

            // Set priority header (RabbitMQ priority queues)
            if (message.getPriority() != null) {
                builder.setPriority(getPriorityValue(message.getPriority()));
            }

            Message rabbitMessage = builder.build();

            rabbitTemplate.send(exchange, routingKey, rabbitMessage);

            log.info("Published email to queue: event={}, priority={}",
                message.getEventId(), message.getPriority());

        } catch (Exception e) {
            log.error("Failed to publish email to queue: {}", message.getEventId(), e);
            throw new RuntimeException("Failed to publish email to queue", e);
        }
    }

    /**
     * Publish an email message to the retry queue with delay.
     *
     * @param message Email message to retry
     * @param delayMs Delay in milliseconds
     */
    public void publishToRetryQueue(EmailMessage message, long delayMs) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);

            Message rabbitMessage = MessageBuilder.withBody(jsonMessage.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setExpiration(String.valueOf(delayMs))
                .setHeader("event_id", message.getEventId())
                .setHeader("retry_count", message.getRetryCount())
                .build();

            rabbitTemplate.send(retryExchange, retryRoutingKey, rabbitMessage);

            log.info("Published email to retry queue: event={}, delay={}ms",
                message.getEventId(), delayMs);

        } catch (Exception e) {
            log.error("Failed to publish email to retry queue: {}", message.getEventId(), e);
        }
    }

    /**
     * Publish an email message to the dead letter queue.
     *
     * @param message Email message that failed
     * @param errorMessage Error message
     */
    public void publishToDeadLetterQueue(EmailMessage message, String errorMessage) {
        try {
            // Add error information to metadata
            message.getMetadata().put("failure_reason", errorMessage);
            message.getMetadata().put("failed_at", java.time.Instant.now().toString());

            String jsonMessage = objectMapper.writeValueAsString(message);

            Message rabbitMessage = MessageBuilder.withBody(jsonMessage.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setHeader("event_id", message.getEventId())
                .setHeader("error", errorMessage)
                .build();

            rabbitTemplate.send("email.dlx", "email.dlq", rabbitMessage);

            log.warn("Published email to DLQ: event={}, error={}",
                message.getEventId(), errorMessage);

        } catch (Exception e) {
            log.error("Failed to publish email to DLQ: {}", message.getEventId(), e);
        }
    }

    /**
     * Get message TTL based on priority.
     *
     * @param priority Priority level
     * @return TTL in milliseconds
     */
    private long getMessageTtl(String priority) {
        return switch (priority) {
            case "critical" -> 60000;      // 1 minute
            case "high" -> 300000;         // 5 minutes
            case "low" -> 3600000;         // 1 hour
            default -> 300000;             // 5 minutes (normal)
        };
    }

    /**
     * Convert priority string to RabbitMQ priority value (0-255).
     *
     * @param priority Priority level
     * @return RabbitMQ priority value
     */
    private int getPriorityValue(String priority) {
        return switch (priority) {
            case "critical" -> 10;
            case "high" -> 7;
            case "low" -> 2;
            default -> 5;
        };
    }
}

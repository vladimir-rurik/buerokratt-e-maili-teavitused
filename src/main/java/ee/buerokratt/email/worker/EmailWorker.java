package ee.buerokratt.email.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.buerokratt.email.model.EmailMessage;
import ee.buerokratt.email.model.EmailResult;
import ee.buerokratt.email.service.QueueService;
import ee.buerokratt.email.service.provider.EmailProvider;
import ee.buerokratt.email.service.provider.EmailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.time.Instant;

/**
 * Worker for processing email messages from RabbitMQ queue.
 *
 * Consumes messages from the email queue, sends them via the
 * configured email provider, and handles retries and failures.
 */
@Component
@ConditionalOnProperty(name = "email.worker.enabled", havingValue = "true", matchIfMissing = true)
public class EmailWorker {

    private static final Logger log = LoggerFactory.getLogger(EmailWorker.class);
    private final EmailProvider emailProvider;
    private final QueueService queueService;
    private final EmailWorkerMetrics metrics;
    private final ObjectMapper objectMapper;

    public EmailWorker(EmailProvider emailProvider, QueueService queueService,
                       EmailWorkerMetrics metrics, ObjectMapper objectMapper) {
        this.emailProvider = emailProvider;
        this.queueService = queueService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Value("${email.retry.max-critical:5}")
    private int maxRetriesCritical;

    @Value("${email.retry.max-high:3}")
    private int maxRetriesHigh;

    @Value("${email.retry.max-normal:2}")
    private int maxRetriesNormal;

    @Value("${email.retry.max-low:1}")
    private int maxRetriesLow;

    /**
     * Process email messages from the main queue.
     *
     * @param jsonMessage JSON message from queue
     * @param channel RabbitMQ channel
     * @param deliveryTag Delivery tag for acknowledgment
     */
    @RabbitListener(
        queues = "email.notifications",
        concurrency = "5-20",
        containerFactory = "rabbitListenerContainerFactory"
    )
    public void processEmail(
        String jsonMessage,
        Channel channel,
        long deliveryTag
    ) {
        EmailMessage message = null;

        try {
            // Parse message
            message = objectMapper.readValue(jsonMessage, EmailMessage.class);
            log.info("Processing email: event={}, to={}", message.getEventId(), message.getTo());

            // Check if scheduled for future
            if (message.getScheduledFor() != null &&
                message.getScheduledFor().isAfter(Instant.now())) {
                log.debug("Email scheduled for future: event={}, time={}",
                    message.getEventId(), message.getScheduledFor());
                // Requeue with delay
                channel.basicNack(deliveryTag, false, true);
                return;
            }

            // Send email
            long startTime = System.currentTimeMillis();
            EmailResult result = emailProvider.send(message);
            long duration = System.currentTimeMillis() - startTime;

            if (result.isSuccess()) {
                handleSuccess(message, result, duration, channel, deliveryTag);
            } else {
                handleFailure(message, result.getError(), channel, deliveryTag);
            }

        } catch (EmailException e) {
            log.error("Email provider error", e);
            handleProviderError(message, e, channel, deliveryTag);
        } catch (Exception e) {
            log.error("Unexpected error processing email", e);
            handleUnexpectedError(message, e, channel, deliveryTag);
        }
    }

    private void handleSuccess(
        EmailMessage message,
        EmailResult result,
        long duration,
        Channel channel,
        long deliveryTag
    ) {
        try {
            log.info("Email sent successfully: event={}, provider={}, duration={}ms",
                message.getEventId(), result.getProvider(), duration);

            // Record metrics
            metrics.recordEmailSent(result.getProvider(), message.getEventType(), duration);

            // Acknowledge message
            channel.basicAck(deliveryTag, false);

            // TODO: Update delivery status in database
            // deliveryStatusService.updateStatus(message.getEventId(), "sent", result);

        } catch (Exception e) {
            log.error("Error handling success", e);
        }
    }

    private void handleFailure(
        EmailMessage message,
        String error,
        Channel channel,
        long deliveryTag
    ) {
        log.warn("Email send failed: event={}, error={}, retries={}",
            message.getEventId(), error, message.getRetryCount());

        message.setRetryCount(message.getRetryCount() + 1);
        message.setAttemptCount(message.getAttemptCount() + 1);

        int maxRetries = getMaxRetries(message.getPriority());

        if (message.getRetryCount() >= maxRetries) {
            // Max retries exceeded - send to DLQ
            log.error("Max retries exceeded, sending to DLQ: event={}", message.getEventId());
            queueService.publishToDeadLetterQueue(message, error);
            metrics.recordEmailFailed(message.getEventType(), "max_retries");
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                log.error("Failed to acknowledge message", e);
            }
        } else {
            // Retry with backoff
            long delay = calculateBackoff(message.getRetryCount());
            log.info("Retrying email: event={}, attempt={}, delay={}ms",
                message.getEventId(), message.getRetryCount(), delay);
            queueService.publishToRetryQueue(message, delay);
            metrics.recordEmailRetry(message.getEventType());
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                log.error("Failed to acknowledge message", e);
            }
        }
    }

    private void handleProviderError(
        EmailMessage message,
        EmailException e,
        Channel channel,
        long deliveryTag
    ) {
        log.error("Provider error for email: event={}, error={}",
            message.getEventId(), e.getMessage());

        message.setRetryCount(message.getRetryCount() + 1);

        // Check if error is retryable
        if (!e.isRetryable() || message.getRetryCount() >= getMaxRetries(message.getPriority())) {
            queueService.publishToDeadLetterQueue(message, e.getMessage());
            metrics.recordEmailFailed(message.getEventType(), "provider_error");
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException ioException) {
                log.error("Failed to acknowledge message", ioException);
            }
        } else {
            long delay = calculateBackoff(message.getRetryCount());
            queueService.publishToRetryQueue(message, delay);
            metrics.recordEmailRetry(message.getEventType());
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException ioException) {
                log.error("Failed to acknowledge message", ioException);
            }
        }
    }

    private void handleUnexpectedError(
        EmailMessage message,
        Exception e,
        Channel channel,
        long deliveryTag
    ) {
        log.error("Unexpected error processing email: event={}", message.getEventId(), e);

        // Send to DLQ without incrementing retry count
        // as this is likely a data/format issue
        queueService.publishToDeadLetterQueue(message, e.getMessage());
        metrics.recordEmailFailed(message.getEventType(), "unexpected_error");

        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception ackException) {
            log.error("Failed to acknowledge message", ackException);
        }
    }

    private long calculateBackoff(int retryCount) {
        // Exponential backoff: 2^n * 1000ms (max 60 seconds)
        return Math.min(60000, (long) Math.pow(2, retryCount) * 1000);
    }

    private int getMaxRetries(String priority) {
        return switch (priority) {
            case "critical" -> maxRetriesCritical;
            case "high" -> maxRetriesHigh;
            case "low" -> maxRetriesLow;
            default -> maxRetriesNormal;
        };
    }
}

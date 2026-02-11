package ee.buerokratt.email.service;

import ee.buerokratt.email.model.DeliveryStatus;
import ee.buerokratt.email.model.EmailMessage;
import ee.buerokratt.email.model.EmailRequest;
import ee.buerokratt.email.model.EmailResult;
import ee.buerokratt.email.service.provider.EmailProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Main service for handling email notifications.
 *
 * Orchestrates the email sending process including validation,
 * enrichment, queueing, and delivery tracking.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final QueueService queueService;
    private final TemplateService templateService;
    private final EmailProvider emailProvider;
    private final RestTemplate restTemplate;

    public EmailService(QueueService queueService, TemplateService templateService,
                        EmailProvider emailProvider, RestTemplate restTemplate) {
        this.queueService = queueService;
        this.templateService = templateService;
        this.emailProvider = emailProvider;
        this.restTemplate = restTemplate;
    }

    @Value("${email.from:noreply@buerokratt.ee}")
    private String fromEmail;

    @Value("${email.reply-to:support@buerokratt.ee}")
    private String replyToEmail;

    @Value("${resql.url:http://resql:8082}")
    private String resqlUrl;

    @Value("${email.idempotency.enabled:true}")
    private boolean idempotencyEnabled;

    private Map<String, Instant> recentEvents = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Email Notification Service initialized");
        log.info("Email provider: {}", emailProvider.getClass().getSimpleName());
        log.info("From: {}", fromEmail);
    }

    /**
     * Send an email notification.
     *
     * @param request Email request
     * @return Result with message ID and status
     */
    public Map<String, Object> sendEmail(EmailRequest request) {
        // Validate request
        validateRequest(request);

        // Check idempotency
        if (idempotencyEnabled && request.getEventId() != null) {
            if (isDuplicate(request.getEventId())) {
                log.warn("Duplicate event detected: {}", request.getEventId());
                return Map.of(
                    "messageId", request.getEventId(),
                    "status", "duplicate",
                    "message", "Event already processed"
                );
            }
        }

        // Generate event ID if not provided
        if (request.getEventId() == null) {
            request.setEventId(UUID.randomUUID().toString());
        }

        // Enrich request with defaults
        enrichRequest(request);

        // Build email message
        EmailMessage message = buildEmailMessage(request);

        // Render template
        templateService.renderTemplate(message);

        // Publish to queue
        queueService.publishEmail(message);

        // Log to database via Resql
        logEmailRequest(request);

        // Track for idempotency
        if (idempotencyEnabled) {
            trackEvent(request.getEventId());
        }

        return Map.of(
            "messageId", request.getEventId(),
            "status", "queued",
            "queuedAt", Instant.now().toString()
        );
    }

    /**
     * Send multiple emails in batch.
     *
     * @param requests List of email requests
     * @return Batch result with count and message IDs
     */
    public Map<String, Object> sendBatch(List<EmailRequest> requests) {
        log.info("Processing batch of {} emails", requests.size());

        // Process in parallel with limited concurrency
        int batchSize = 10;
        List<Map<String, Object>> results = new ArrayList<>();

        List<List<EmailRequest>> batches = partition(requests, batchSize);

        for (List<EmailRequest> batch : batches) {
            List<CompletableFuture<Map<String, Object>>> futures = batch.stream()
                .map(req -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return sendEmail(req);
                    } catch (Exception e) {
                        log.error("Error sending email in batch", e);
                        return Map.<String, Object>of("error", e.getMessage());
                    }
                }))
                .toList();

            // Wait for batch to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect results
            results.addAll(futures.stream()
                .map(CompletableFuture::join)
                .toList());
        }

        int successCount = (int) results.stream()
            .filter(r -> r.containsKey("messageId"))
            .count();

        return Map.of(
            "total", requests.size(),
            "success", successCount,
            "failed", requests.size() - successCount,
            "results", results
        );
    }

    /**
     * Get delivery status for an email.
     *
     * @param messageId Event/message ID
     * @return Delivery status
     */
    public DeliveryStatus getDeliveryStatus(String messageId) {
        try {
            Map<String, Object> response = restTemplate.postForObject(
                resqlUrl + "/get-email-status",
                Map.of("eventId", messageId),
                Map.class
            );

            if (response != null && response.containsKey("body")) {
                List<Map<String, Object>> records = (List<Map<String, Object>>) response.get("body");
                if (!records.isEmpty()) {
                    Map<String, Object> record = records.get(0);
                    return DeliveryStatus.builder()
                        .eventId((String) record.get("event_id"))
                        .status((String) record.get("status"))
                        .provider((String) record.get("provider"))
                        .providerMessageId((String) record.get("provider_message_id"))
                        .attempts((Integer) record.get("retry_count"))
                        .lastError((String) record.get("error_message"))
                        .createdAt(parseInstant((String) record.get("created_at")))
                        .sentAt(parseInstant((String) record.get("sent_at")))
                        .deliveredAt(parseInstant((String) record.get("delivered_at")))
                        .failedAt(parseInstant((String) record.get("failed_at")))
                        .build();
                }
            }
        } catch (Exception e) {
            log.error("Error fetching delivery status for: {}", messageId, e);
        }

        return null;
    }

    /**
     * Retry a failed email.
     *
     * @param messageId Event/message ID
     */
    public void retryEmail(String messageId) {
        log.info("Retrying email: {}", messageId);

        // Fetch original request from database
        // Re-queue with incremented retry count
        // Implementation depends on database schema
        throw new UnsupportedOperationException("Retry not yet implemented");
    }

    /**
     * Cancel a scheduled email.
     *
     * @param messageId Event/message ID
     * @return True if cancelled
     */
    public boolean cancelEmail(String messageId) {
        log.info("Cancelling email: {}", messageId);

        // Check if email is still queued and cancel if possible
        // Implementation depends on queue configuration
        return false;
    }

    private void validateRequest(EmailRequest request) {
        if (request.getRecipientEmail() == null || request.getRecipientEmail().isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        if (request.getEventType() == null || request.getEventType().isBlank()) {
            throw new IllegalArgumentException("Event type is required");
        }

        if (request.getPriority() != null &&
            !Arrays.asList("low", "normal", "high", "critical").contains(request.getPriority())) {
            throw new IllegalArgumentException("Invalid priority level");
        }
    }

    private void enrichRequest(EmailRequest request) {
        if (request.getPriority() == null) {
            request.setPriority("normal");
        }

        if (request.getLocale() == null) {
            request.setLocale("et");
        }

        if (request.getRetryCount() == null) {
            request.setRetryCount(0);
        }
    }

    private EmailMessage buildEmailMessage(EmailRequest request) {
        return EmailMessage.builder()
            .eventId(request.getEventId())
            .eventType(request.getEventType())
            .to(request.getRecipientEmail())
            .recipientName(request.getRecipientName())
            .from(fromEmail)
            .replyTo(replyToEmail)
            .templateId(request.getTemplateId())
            .priority(request.getPriority())
            .locale(request.getLocale())
            .templateData(request.getTemplateData())
            .metadata(request.getMetadata())
            .retryCount(request.getRetryCount())
            .maxRetries(getMaxRetries(request.getPriority()))
            .scheduledFor(request.getScheduledFor())
            .createdAt(Instant.now())
            .attemptCount(0)
            .build();
    }

    private int getMaxRetries(String priority) {
        return switch (priority) {
            case "critical" -> 5;
            case "high" -> 3;
            case "low" -> 1;
            default -> 2;
        };
    }

    private boolean isDuplicate(String eventId) {
        Instant lastSeen = recentEvents.get(eventId);
        if (lastSeen == null) {
            return false;
        }
        // Consider duplicate if seen in the last 24 hours
        return lastSeen.isAfter(Instant.now().minusSeconds(86400));
    }

    private void trackEvent(String eventId) {
        recentEvents.put(eventId, Instant.now());

        // Clean old entries
        Instant cutoff = Instant.now().minusSeconds(86400);
        recentEvents.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }

    private void logEmailRequest(EmailRequest request) {
        try {
            restTemplate.postForObject(
                resqlUrl + "/log-email-request",
                Map.of(
                    "eventId", request.getEventId(),
                    "eventType", request.getEventType(),
                    "recipientEmail", request.getRecipientEmail(),
                    "templateId", request.getTemplateId(),
                    "priority", request.getPriority(),
                    "status", "queued",
                    "createdAt", Instant.now().toString()
                ),
                Map.class
            );
        } catch (Exception e) {
            log.error("Failed to log email request to Resql", e);
        }
    }

    private Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
}

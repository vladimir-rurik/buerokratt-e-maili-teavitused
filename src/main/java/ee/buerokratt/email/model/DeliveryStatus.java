package ee.buerokratt.email.model;

import java.time.Instant;

/**
 * Delivery status of an email notification.
 *
 * Tracks the current state of an email through its lifecycle,
 * from queued to delivered or failed.
 */
public class DeliveryStatus {

    /**
     * Unique event identifier.
     */
    private String eventId;

    /**
     * Current delivery status.
     * Values: queued, processing, sent, delivered, failed, retried, dlq
     */
    private String status;

    /**
     * Email provider used.
     */
    private String provider;

    /**
     * Provider's message ID.
     */
    private String providerMessageId;

    /**
     * Number of delivery attempts.
     */
    private Integer attempts;

    /**
     * Last error message (if failed).
     */
    private String lastError;

    /**
     * Timestamp when the email was created.
     */
    private Instant createdAt;

    /**
     * Timestamp when the email was sent.
     */
    private Instant sentAt;

    /**
     * Timestamp when delivery was confirmed.
     */
    private Instant deliveredAt;

    /**
     * Timestamp when the email failed.
     */
    private Instant failedAt;

    /**
     * Estimated delivery time (if queued).
     */
    private Instant estimatedDeliveryAt;

    // Default constructor
    public DeliveryStatus() {}

    // Getters
    public String getEventId() { return eventId; }
    public String getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public Integer getAttempts() { return attempts; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getEstimatedDeliveryAt() { return estimatedDeliveryAt; }

    // Setters
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setStatus(String status) { this.status = status; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }
    public void setEstimatedDeliveryAt(Instant estimatedDeliveryAt) { this.estimatedDeliveryAt = estimatedDeliveryAt; }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private DeliveryStatus status = new DeliveryStatus();

        public Builder eventId(String eventId) { status.eventId = eventId; return this; }
        public Builder status(String status) { this.status.status = status; return this; }
        public Builder provider(String provider) { status.provider = provider; return this; }
        public Builder providerMessageId(String providerMessageId) { status.providerMessageId = providerMessageId; return this; }
        public Builder attempts(Integer attempts) { status.attempts = attempts; return this; }
        public Builder lastError(String lastError) { status.lastError = lastError; return this; }
        public Builder createdAt(Instant createdAt) { status.createdAt = createdAt; return this; }
        public Builder sentAt(Instant sentAt) { status.sentAt = sentAt; return this; }
        public Builder deliveredAt(Instant deliveredAt) { status.deliveredAt = deliveredAt; return this; }
        public Builder failedAt(Instant failedAt) { status.failedAt = failedAt; return this; }
        public Builder estimatedDeliveryAt(Instant estimatedDeliveryAt) { status.estimatedDeliveryAt = estimatedDeliveryAt; return this; }

        public DeliveryStatus build() {
            return status;
        }
    }
}

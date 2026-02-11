package ee.buerokratt.email.model;

import java.time.Instant;
import java.util.Map;

/**
 * Internal email message model for RabbitMQ queue processing.
 *
 * This class represents the message structure that flows through
 * the RabbitMQ queues and is processed by email workers.
 */
public class EmailMessage {

    /**
     * Unique event identifier.
     */
    private String eventId;

    /**
     * Type of email event.
     */
    private String eventType;

    /**
     * Recipient email address.
     */
    private String to;

    /**
     * Recipient name.
     */
    private String recipientName;

    /**
     * Sender email address.
     */
    private String from;

    /**
     * Reply-to address.
     */
    private String replyTo;

    /**
     * Email subject.
     */
    private String subject;

    /**
     * HTML email body.
     */
    private String htmlBody;

    /**
     * Plain text email body.
     */
    private String textBody;

    /**
     * Template ID used for rendering.
     */
    private String templateId;

    /**
     * Priority level.
     */
    private String priority;

    /**
     * Locale code.
     */
    private String locale;

    /**
     * Template data for rendering.
     */
    private Map<String, Object> templateData;

    /**
     * Additional metadata.
     */
    private Map<String, String> metadata;

    /**
     * Current retry count.
     */
    private Integer retryCount;

    /**
     * Maximum allowed retries.
     */
    private Integer maxRetries;

    /**
     * Scheduled send time for delayed delivery.
     */
    private Instant scheduledFor;

    /**
     * Creation timestamp.
     */
    private Instant createdAt;

    /**
     * Number of delivery attempts.
     */
    private Integer attemptCount;

    // Default constructor
    public EmailMessage() {}

    // Getters
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getTo() { return to; }
    public String getRecipientName() { return recipientName; }
    public String getFrom() { return from; }
    public String getReplyTo() { return replyTo; }
    public String getSubject() { return subject; }
    public String getHtmlBody() { return htmlBody; }
    public String getTextBody() { return textBody; }
    public String getTemplateId() { return templateId; }
    public String getPriority() { return priority; }
    public String getLocale() { return locale; }
    public Map<String, Object> getTemplateData() { return templateData; }
    public Map<String, String> getMetadata() { return metadata; }
    public Integer getRetryCount() { return retryCount; }
    public Integer getMaxRetries() { return maxRetries; }
    public Instant getScheduledFor() { return scheduledFor; }
    public Instant getCreatedAt() { return createdAt; }
    public Integer getAttemptCount() { return attemptCount; }

    // Setters
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setTo(String to) { this.to = to; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public void setFrom(String from) { this.from = from; }
    public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
    public void setTextBody(String textBody) { this.textBody = textBody; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setLocale(String locale) { this.locale = locale; }
    public void setTemplateData(Map<String, Object> templateData) { this.templateData = templateData; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EmailMessage message = new EmailMessage();

        public Builder eventId(String eventId) { message.eventId = eventId; return this; }
        public Builder eventType(String eventType) { message.eventType = eventType; return this; }
        public Builder to(String to) { message.to = to; return this; }
        public Builder recipientName(String recipientName) { message.recipientName = recipientName; return this; }
        public Builder from(String from) { message.from = from; return this; }
        public Builder replyTo(String replyTo) { message.replyTo = replyTo; return this; }
        public Builder subject(String subject) { message.subject = subject; return this; }
        public Builder htmlBody(String htmlBody) { message.htmlBody = htmlBody; return this; }
        public Builder textBody(String textBody) { message.textBody = textBody; return this; }
        public Builder templateId(String templateId) { message.templateId = templateId; return this; }
        public Builder priority(String priority) { message.priority = priority; return this; }
        public Builder locale(String locale) { message.locale = locale; return this; }
        public Builder templateData(Map<String, Object> templateData) { message.templateData = templateData; return this; }
        public Builder metadata(Map<String, String> metadata) { message.metadata = metadata; return this; }
        public Builder retryCount(Integer retryCount) { message.retryCount = retryCount; return this; }
        public Builder maxRetries(Integer maxRetries) { message.maxRetries = maxRetries; return this; }
        public Builder scheduledFor(Instant scheduledFor) { message.scheduledFor = scheduledFor; return this; }
        public Builder createdAt(Instant createdAt) { message.createdAt = createdAt; return this; }
        public Builder attemptCount(Integer attemptCount) { message.attemptCount = attemptCount; return this; }

        public EmailMessage build() {
            return message;
        }
    }
}

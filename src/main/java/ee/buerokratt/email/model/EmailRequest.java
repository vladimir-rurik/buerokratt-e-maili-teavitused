package ee.buerokratt.email.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;

/**
 * Email request model for incoming email notification requests.
 *
 * This class represents the structure of an email request received from
 * Ruuter DSL workflows or direct API calls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailRequest {

    /**
     * Unique event identifier for tracking and idempotency.
     * If not provided, a UUID will be generated.
     */
    private String eventId;

    /**
     * Type of email event (e.g., user_registration, password_reset).
     * Used for template selection and analytics.
     */
    @NotBlank(message = "Event type is required")
    private String eventType;

    /**
     * Recipient email address.
     */
    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String recipientEmail;

    /**
     * Recipient's name for personalization.
     */
    private String recipientName;

    /**
     * Template identifier for rendering email content.
     * Templates are stored in the database and support multiple locales.
     */
    private String templateId;

    /**
     * Data for template rendering.
     * Variables will be available in the template context.
     */
    private Map<String, Object> templateData;

    /**
     * Email priority level affecting retry policies.
     * Allowed values: low, normal, high, critical
     */
    @Pattern(regexp = "low|normal|high|critical", message = "Invalid priority level")
    private String priority = "normal";

    /**
     * Locale for template and email content.
     * Supported: et, en, ru
     */
    private String locale = "et";

    /**
     * Additional metadata for tracking and analytics.
     * Not used in email rendering but stored with delivery records.
     */
    private Map<String, String> metadata;

    /**
     * Current retry count (used internally).
     */
    private Integer retryCount = 0;

    /**
     * Scheduled time for delayed sending.
     * If null, email is sent immediately.
     */
    private Instant scheduledFor;

    // Default constructor
    public EmailRequest() {}

    // Getters
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getRecipientName() { return recipientName; }
    public String getTemplateId() { return templateId; }
    public Map<String, Object> getTemplateData() { return templateData; }
    public String getPriority() { return priority; }
    public String getLocale() { return locale; }
    public Map<String, String> getMetadata() { return metadata; }
    public Integer getRetryCount() { return retryCount; }
    public Instant getScheduledFor() { return scheduledFor; }

    // Setters
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public void setTemplateData(Map<String, Object> templateData) { this.templateData = templateData; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setLocale(String locale) { this.locale = locale; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public void setScheduledFor(Instant scheduledFor) { this.scheduledFor = scheduledFor; }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EmailRequest request = new EmailRequest();

        public Builder eventId(String eventId) { request.eventId = eventId; return this; }
        public Builder eventType(String eventType) { request.eventType = eventType; return this; }
        public Builder recipientEmail(String recipientEmail) { request.recipientEmail = recipientEmail; return this; }
        public Builder recipientName(String recipientName) { request.recipientName = recipientName; return this; }
        public Builder templateId(String templateId) { request.templateId = templateId; return this; }
        public Builder templateData(Map<String, Object> templateData) { request.templateData = templateData; return this; }
        public Builder priority(String priority) { request.priority = priority; return this; }
        public Builder locale(String locale) { request.locale = locale; return this; }
        public Builder metadata(Map<String, String> metadata) { request.metadata = metadata; return this; }
        public Builder retryCount(Integer retryCount) { request.retryCount = retryCount; return this; }
        public Builder scheduledFor(Instant scheduledFor) { request.scheduledFor = scheduledFor; return this; }

        public EmailRequest build() {
            return request;
        }
    }
}

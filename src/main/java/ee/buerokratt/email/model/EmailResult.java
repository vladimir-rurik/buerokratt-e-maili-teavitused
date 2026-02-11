package ee.buerokratt.email.model;

import java.time.Instant;

/**
 * Result of an email send operation.
 *
 * This class contains information about the outcome of an email
 * send attempt, including success status, provider message ID,
 * and timing information.
 */
public class EmailResult {

    /**
     * True if email was sent successfully.
     */
    private boolean success;

    /**
     * Message ID from the email provider.
     * Used for tracking delivery status.
     */
    private String messageId;

    /**
     * Email provider name (smtp, ses, sendgrid).
     */
    private String provider;

    /**
     * HTTP status code from provider (if applicable).
     */
    private Integer statusCode;

    /**
     * Error message if send failed.
     */
    private String error;

    /**
     * Exception details if send failed.
     */
    private Throwable exception;

    /**
     * Timestamp when the email was sent.
     */
    private Instant timestamp;

    /**
     * Duration of the send operation in milliseconds.
     */
    private Long duration;

    /**
     * Additional provider-specific data.
     */
    private Object providerData;

    // Default constructor
    public EmailResult() {}

    // Getters
    public boolean isSuccess() { return success; }
    public String getMessageId() { return messageId; }
    public String getProvider() { return provider; }
    public Integer getStatusCode() { return statusCode; }
    public String getError() { return error; }
    public Throwable getException() { return exception; }
    public Instant getTimestamp() { return timestamp; }
    public Long getDuration() { return duration; }
    public Object getProviderData() { return providerData; }

    // Setters
    public void setSuccess(boolean success) { this.success = success; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public void setError(String error) { this.error = error; }
    public void setException(Throwable exception) { this.exception = exception; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setDuration(Long duration) { this.duration = duration; }
    public void setProviderData(Object providerData) { this.providerData = providerData; }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EmailResult result = new EmailResult();

        public Builder success(boolean success) { result.success = success; return this; }
        public Builder messageId(String messageId) { result.messageId = messageId; return this; }
        public Builder provider(String provider) { result.provider = provider; return this; }
        public Builder statusCode(Integer statusCode) { result.statusCode = statusCode; return this; }
        public Builder error(String error) { result.error = error; return this; }
        public Builder exception(Throwable exception) { result.exception = exception; return this; }
        public Builder timestamp(Instant timestamp) { result.timestamp = timestamp; return this; }
        public Builder duration(Long duration) { result.duration = duration; return this; }
        public Builder providerData(Object providerData) { result.providerData = providerData; return this; }

        public EmailResult build() {
            return result;
        }
    }
}

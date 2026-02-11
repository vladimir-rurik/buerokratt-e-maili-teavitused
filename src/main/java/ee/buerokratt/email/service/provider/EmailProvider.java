package ee.buerokratt.email.service.provider;

import ee.buerokratt.email.model.EmailMessage;
import ee.buerokratt.email.model.EmailResult;

/**
 * Interface for email provider implementations.
 *
 * Supported implementations:
 * - SMTP (SmtpEmailProvider)
 * - AWS SES (SesEmailProvider)
 * - SendGrid (SendGridEmailProvider)
 */
public interface EmailProvider {

    /**
     * Send an email message.
     *
     * @param message Email message to send
     * @return Email send result
     * @throws EmailException if sending fails
     */
    EmailResult send(EmailMessage message) throws EmailException;

    /**
     * Get provider name.
     *
     * @return Provider identifier
     */
    String getProviderName();

    /**
     * Check if provider is healthy.
     *
     * @return true if provider is operational
     */
    default boolean isHealthy() {
        return true;
    }
}

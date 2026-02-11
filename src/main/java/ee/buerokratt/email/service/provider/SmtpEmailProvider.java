package ee.buerokratt.email.service.provider;

import ee.buerokratt.email.model.EmailMessage;
import ee.buerokratt.email.model.EmailResult;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Properties;

/**
 * SMTP email provider implementation.
 *
 * Uses standard JavaMail API for sending emails via SMTP server.
 */
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "smtp")
public class SmtpEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private JavaMailSender mailSender;

    @Value("${email.smtp.host}")
    private String smtpHost;

    @Value("${email.smtp.port:587}")
    private int smtpPort;

    @Value("${email.smtp.username:}")
    private String username;

    @Value("${email.smtp.password:}")
    private String password;

    @Value("${email.smtp.from:}")
    private String fromEmail;

    @Value("${email.smtp.auth:true}")
    private boolean authEnabled;

    @Value("${email.smtp.starttls:true}")
    private boolean starttlsEnabled;

    @Value("${email.smtp.debug:false}")
    private boolean debug;

    @PostConstruct
    public void init() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(smtpHost);
        mailSender.setPort(smtpPort);

        if (authEnabled && username != null && !username.isBlank()) {
            mailSender.setUsername(username);
            mailSender.setPassword(password);
        }

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(authEnabled));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttlsEnabled));
        props.put("mail.debug", String.valueOf(debug));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        this.mailSender = mailSender;

        log.info("SMTP Email Provider initialized: {}:{}", smtpHost, smtpPort);
    }

    @Override
    public EmailResult send(EmailMessage message) throws EmailException {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("Sending email via SMTP: to={}, subject={}",
                message.getTo(), message.getSubject());

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage,
                true,
                "UTF-8"
            );

            // Set from address
            String from = message.getFrom() != null ? message.getFrom() : fromEmail;
            if (from == null || from.isBlank()) {
                throw new EmailException("From address is not configured");
            }

            helper.setFrom(from);
            helper.setTo(message.getTo());
            helper.setSubject(message.getSubject() != null ? message.getSubject() : "");

            // Set reply-to if provided
            if (message.getReplyTo() != null && !message.getReplyTo().isBlank()) {
                helper.setReplyTo(message.getReplyTo());
            }

            // Set email body
            if (message.getTextBody() != null && message.getHtmlBody() != null) {
                helper.setText(message.getTextBody(), message.getHtmlBody());
            } else if (message.getHtmlBody() != null) {
                helper.setText(message.getHtmlBody(), true);
            } else if (message.getTextBody() != null) {
                helper.setText(message.getTextBody(), false);
            }

            // Send email
            mailSender.send(mimeMessage);

            long duration = System.currentTimeMillis() - startTime;

            log.info("Email sent successfully via SMTP: to={}, duration={}ms",
                message.getTo(), duration);

            return EmailResult.builder()
                .success(true)
                .messageId(message.getEventId())
                .provider(getProviderName())
                .timestamp(Instant.now())
                .duration(duration)
                .build();

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;

            log.error("Failed to send email via SMTP: to={}, error={}",
                message.getTo(), e.getMessage());

            throw new EmailException(
                "Failed to send email via SMTP: " + e.getMessage(),
                e,
                "smtp"
            );
        }
    }

    @Override
    public String getProviderName() {
        return "smtp";
    }

    @Override
    public boolean isHealthy() {
        try {
            if (mailSender instanceof JavaMailSenderImpl) {
                ((JavaMailSenderImpl) mailSender).testConnection();
            }
            return true;
        } catch (Exception e) {
            log.warn("SMTP connection test failed: {}", e.getMessage());
            return false;
        }
    }
}

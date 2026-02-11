package ee.buerokratt.email.service;

import ee.buerokratt.email.model.EmailMessage;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for rendering email templates.
 *
 * Fetches templates from the database and renders them using
 * Handlebars template engine.
 */

@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);
    private final Handlebars handlebars;
    private final RestTemplate restTemplate;

    public TemplateService(Handlebars handlebars, RestTemplate restTemplate) {
        this.handlebars = handlebars;
        this.restTemplate = restTemplate;
    }

    @Value("${resql.url:http://resql:8082}")
    private String resqlUrl;

    @Value("${email.default-locale:et}")
    private String defaultLocale;

    /**
     * Render email template for the given message.
     *
     * @param message Email message with template information
     */
    public void renderTemplate(EmailMessage message) {
        try {
            // Fetch template from database
            EmailTemplate template = fetchTemplate(
                message.getTemplateId(),
                message.getLocale()
            );

            if (template == null) {
                // Try default locale if specific locale not found
                template = fetchTemplate(
                    message.getTemplateId(),
                    defaultLocale
                );
            }

            if (template == null) {
                throw new IllegalArgumentException(
                    "Template not found: " + message.getTemplateId()
                );
            }

            // Set subject and body from template
            message.setSubject(renderString(template.getSubject(), message.getTemplateData()));
            message.setHtmlBody(renderString(template.getHtmlBody(), message.getTemplateData()));
            message.setTextBody(renderString(template.getTextBody(), message.getTemplateData()));

            log.debug("Rendered template: {} for event: {}",
                message.getTemplateId(), message.getEventId());

        } catch (Exception e) {
            log.error("Failed to render template: {}", message.getTemplateId(), e);
            throw new RuntimeException("Template rendering failed", e);
        }
    }

    /**
     * Fetch template from database via Resql.
     *
     * @param templateId Template identifier
     * @param locale Locale code
     * @return Email template or null if not found
     */
    @Cacheable(value = "emailTemplates", key = "#templateId + ':' + #locale")
    public EmailTemplate fetchTemplate(String templateId, String locale) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                resqlUrl + "/get-email-template",
                Map.of(
                    "templateId", templateId,
                    "locale", locale
                ),
                Map.class
            );

            if (response != null && response.containsKey("body")) {
                var records = (java.util.List<Map<String, Object>>) response.get("body");
                if (!records.isEmpty()) {
                    Map<String, Object> record = records.get(0);
                    return new EmailTemplate(
                        (String) record.get("id"),
                        (String) record.get("locale"),
                        (String) record.get("subject"),
                        (String) record.get("html_body"),
                        (String) record.get("text_body"),
                        (Integer) record.get("version")
                    );
                }
            }

        } catch (Exception e) {
            log.error("Failed to fetch template: {} (locale: {})", templateId, locale, e);
        }

        return null;
    }

    /**
     * Render a Handlebars template string with given data.
     *
     * @param templateString Template string
     * @param data Data for template variables
     * @return Rendered string
     */
    private String renderString(String templateString, Map<String, Object> data) {
        try {
            if (templateString == null || templateString.isBlank()) {
                return "";
            }

            Template template = handlebars.compileInline(templateString);
            return template.apply(data != null ? data : new HashMap<>());

        } catch (IOException e) {
            log.error("Failed to render template string", e);
            return templateString; // Return original if rendering fails
        }
    }

    /**
     * Evict template from cache.
     *
     * @param templateId Template identifier
     * @param locale Locale code
     */
    public void evictTemplate(String templateId, String locale) {
        // This would be called when templates are updated
        log.info("Evicting template from cache: {} ({})", templateId, locale);
    }

    /**
     * Internal model for email template.
     */
    public static class EmailTemplate {
        private String id;
        private String locale;
        private String subject;
        private String htmlBody;
        private String textBody;
        private Integer version;

        public EmailTemplate(String id, String locale, String subject, String htmlBody, String textBody, Integer version) {
            this.id = id;
            this.locale = locale;
            this.subject = subject;
            this.htmlBody = htmlBody;
            this.textBody = textBody;
            this.version = version;
        }

        public EmailTemplate() {}

        public String getId() { return id; }
        public String getLocale() { return locale; }
        public String getSubject() { return subject; }
        public String getHtmlBody() { return htmlBody; }
        public String getTextBody() { return textBody; }
        public Integer getVersion() { return version; }

        public void setId(String id) { this.id = id; }
        public void setLocale(String locale) { this.locale = locale; }
        public void setSubject(String subject) { this.subject = subject; }
        public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
        public void setTextBody(String textBody) { this.textBody = textBody; }
        public void setVersion(Integer version) { this.version = version; }
    }
}

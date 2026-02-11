package ee.buerokratt.email.config;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.helper.StringHelpers;
import ee.buerokratt.email.service.provider.EmailProvider;
import ee.buerokratt.email.service.provider.SmtpEmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for email providers and related beans.
 */
@Configuration
public class EmailProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailProviderConfig.class);

    @Bean
    public Handlebars handlebars() {
        Handlebars handlebars = new Handlebars();

        // Register helpers
        handlebars.registerHelpers(StringHelpers.class);
        handlebars.registerHelper("eq", (context, options) -> {
            Object a = context;
            Object b = options.param(0);
            return a != null && a.equals(b);
        });

        return handlebars;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    @ConditionalOnProperty(name = "email.provider", havingValue = "smtp")
    public EmailProvider smtpEmailProvider() {
        log.info("Using SMTP email provider");
        return new SmtpEmailProvider();
    }

    // Additional providers can be added here:
    // - @Bean @ConditionalOnProperty(name = "email.provider", havingValue = "ses")
    // - @Bean @ConditionalOnProperty(name = "email.provider", havingValue = "sendgrid")
}

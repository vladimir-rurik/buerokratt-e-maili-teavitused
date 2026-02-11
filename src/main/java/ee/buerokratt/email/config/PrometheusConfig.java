package ee.buerokratt.email.config;

import ee.buerokratt.email.worker.EmailWorkerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus metrics configuration.
 *
 * Configures custom metrics for email notifications.
 */
@Configuration
public class PrometheusConfig {

    @Value("${spring.application.name:email-notification-service}")
    private String applicationName;

    @Bean
    public EmailWorkerMetrics emailWorkerMetrics(MeterRegistry registry) {
        return new EmailWorkerMetrics(registry, applicationName);
    }
}

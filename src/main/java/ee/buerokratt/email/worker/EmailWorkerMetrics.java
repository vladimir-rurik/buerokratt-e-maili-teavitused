package ee.buerokratt.email.worker;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus metrics for email worker.
 *
 * Tracks email sending statistics, success rates, failures,
 * retry attempts, and processing times.
 */
@Component
public class EmailWorkerMetrics {

    private static final Logger log = LoggerFactory.getLogger(EmailWorkerMetrics.class);

    private final MeterRegistry registry;
    private final String applicationName;
    private final AtomicLong queueSize = new AtomicLong(0);

    public EmailWorkerMetrics(MeterRegistry registry, String applicationName) {
        this.registry = registry;
        this.applicationName = applicationName;

        // Queue size gauge
        Gauge.builder("email_queue_size", queueSize, AtomicLong::get)
            .description("Current size of email processing queue")
            .tag("application", applicationName)
            .register(registry);
    }

    /**
     * Record successful email send.
     *
     * @param provider Email provider name
     * @param eventType Event type
     * @param durationMs Duration in milliseconds
     */
    public void recordEmailSent(String provider, String eventType, long durationMs) {
        Counter.builder("email_sent_total")
            .tag("application", applicationName)
            .tag("provider", provider)
            .tag("event_type", eventType)
            .register(registry)
            .increment();

        // Record duration as a timer
        Timer.builder("email_send_duration_seconds")
            .tag("application", applicationName)
            .tag("provider", provider)
            .description("Email send duration in seconds")
            .register(registry)
            .record(java.time.Duration.ofMillis(durationMs));
    }

    /**
     * Record failed email.
     *
     * @param eventType Event type
     * @param errorType Error type
     */
    public void recordEmailFailed(String eventType, String errorType) {
        Counter.builder("email_failed_total")
            .tag("application", applicationName)
            .tag("event_type", eventType)
            .tag("error_type", errorType)
            .register(registry)
            .increment();
    }

    /**
     * Record email retry attempt.
     *
     * @param eventType Event type
     */
    public void recordEmailRetry(String eventType) {
        Counter.builder("email_retry_total")
            .tag("application", applicationName)
            .tag("event_type", eventType)
            .register(registry)
            .increment();
    }

    /**
     * Update queue size.
     *
     * @param size Current queue size
     */
    public void setQueueSize(long size) {
        queueSize.set(size);
    }

    /**
     * Increment queue size.
     */
    public void incrementQueueSize() {
        queueSize.incrementAndGet();
    }

    /**
     * Decrement queue size.
     */
    public void decrementQueueSize() {
        queueSize.decrementAndGet();
    }
}

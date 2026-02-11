# Sündmuspõhise E-posti Teavitussüsteemi Lahendus

## Kuidas lahendada sündmuspõhine e-maili teavituste süsteem?

### Arhitektuurilised põhimõtted

**Sündmuse vastuvõtmine ja töötlus:**

Ruuter DSL töövoog kutsuvad REST API otspunkti `/email/send`, mis valideerib päringut ja avaldab sõnumi RabbitMQ järjekorda `email.notifications`. Asyncronsed workerid (5-20 konkurentset tarbijat) töötlevad sõnumid ja delegeerivad saatmise e-posti teenusepakkujale.

```yaml
# DSL/Ruuter.private/email/POST/send-welcome-email.yml
prepare_email:
  assign:
    emailRequest:
      eventType: "user_registration"
      recipientEmail: ${email}
      templateId: "welcome-email"
  next: send_email

send_email:
  call: http.post
  args:
    url: "[#EMAIL_NOTIFICATION_SERVICE]/email/send"
    body: ${emailRequest}
  result: email_result
```

**EmailProvider abstraktsioon:**

```java
public interface EmailProvider {
    EmailResult send(EmailMessage message) throws EmailException;
    String getProviderName();
    boolean isHealthy();
}

@Component
public class SmtpEmailProvider implements EmailProvider {

    private final JavaMailSender mailSender;

    @Override
    public EmailResult send(EmailMessage message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

        helper.setTo(message.getRecipientEmail());
        helper.setSubject(message.getSubject());
        helper.setText(message.getHtmlBody(), true);

        mailSender.send(mimeMessage);
        return EmailResult.success("smtp-id-" + UUID.randomUUID());
    }
}
```

**Taaskatse poliitika (Exponential backoff):**

- Retry 1: 2 sekundit
- Retry 2: 4 sekundit
- Retry 3: 8 sekundit
- Retry 4: 16 sekundit
- Retry 5: 32 sekundit
- Maksimum: 60 sekundit

Prioriteedipõhised piirangud:
- Critical: 5 korduskatset
- High: 3 korduskatset
- Normal: 2 korduskatset
- Low: 1 korduskatse

**Dead Letter Queue (DLQ):**

Kui kõik katsed on nurjunud, liigub sõnum `email.dlq` järjekorda. DLQ sõnumid säilivad 24 tundi ja monitortakse Prometheus häirega, mis triggerdub, kui DLQ kasvab kiiremini kui 10 sõnumit minutis.

## Kuidas lahendada integratsioon olemasoleva ökosüsteemiga?

### TIM (Token Identity Manager)

**JWT autentimine ja autoriseerimine:**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${tim.jwks-url}")
    private String jwksUrl;

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.decoder(jwtDecoder()))
        );

        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/email/send").hasAuthority("SCOPE_email:send")
            .requestMatchers("/email/status/**").hasAuthority("SCOPE_email:read")
        );

        return http.build();
    }
}
```

Scope-põhine autoriseerimine tagab, et ainult volitatud teenused saavad e-kirju saata.

### Resql (Data Service)

**Andmebaasi tabelid:**

```sql
CREATE TABLE email_deliveries (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255) UNIQUE NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    provider VARCHAR(50),
    provider_message_id VARCHAR(255),
    attempts INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    error_message TEXT
);

CREATE TABLE email_templates (
    id VARCHAR(100) PRIMARY KEY,
    locale VARCHAR(10) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    html_body TEXT NOT NULL,
    text_body TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Resql kaudu päringute tegemine:**

```java
@Value("${resql.url}")
private String resqlUrl;

public void saveDeliveryRecord(EmailMessage message, EmailResult result) {
    restTemplate.postForObject(resqlUrl + "/email_deliveries",
        Map.of(
            "event_id", message.getEventId(),
            "recipient_email", message.getRecipientEmail(),
            "event_type", message.getEventType(),
            "status", result.getStatus(),
            "provider", result.getProvider(),
            "provider_message_id", result.getProviderMessageId()
        ),
        Void.class
    );
}
```

### CronManager

**Ajastatud ülesanded:**

```yaml
# DSL/CronManager/email-notifications.yml
retry_queue_processing:
  cron: "*/5 * * * *"  # Iga 5 minutit
  workflow: email_retry_queue

dlq_monitoring:
  cron: "*/10 * * * *"  # Iga 10 minutit
  workflow: email_dlq_monitor

old_records_cleanup:
  cron: "0 2 * * *"  # Iga päev kell 2:00
  workflow: email_cleanup_old_records

template_cache_refresh:
  cron: "0 * * * *"  # Iga tund
  workflow: email_refresh_template_cache
```

### Ruuter DSL Integratsioon

**Töövoogude definitsioonid:**

- `send-welcome-email.yml` - Kasutaja registreerimisel
- `send-password-reset.yml` - Parooli lähtestamisel
- `send-chat-transfer-notification.yml` - Vestluse üleviimisel inimesele

Kõik töövoogude failid asuvad `DSL/Ruuter.private/email/POST/` kataloogis ja kasutavad ühist `http.post` operatsiooni, mis kutsuvad e-posti teenuse REST API.

## Kuidas tagada logimine ja veahalduse jälgitavus?

### Distributed Tracing (OpenTelemetry)

**Trace ID edastus läbi teenuste:**

```yaml
# application.yml
opentelemetry:
  exporter:
    otlp:
      endpoint: http://jaeger:4317
  traces:
    exporter: otlp
```

Iga päring saab unikaalse `traceparent` headeri, mis võimaldab jälgida sündmuste voo alates Ruuter DSL töövoost kuni e-posti kohaletoimetamiseni.

```java
@NewSpan("email-send")
public EmailResult sendEmail(EmailMessage message) {
    Span span = tracer.nextSpan()
        .name("email-provider-send")
        .tag("provider", message.getProvider())
        .tag("recipient", message.getRecipientEmail());

    try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
        return emailProvider.send(message);
    } finally {
        span.end();
    }
}
```

### Struktureeritud logimine (JSON)

**Logimine OpenSearchisse:**

```java
@Slf4j
@Component
public class EmailWorker {

    @RabbitListener(queues = "email.notifications")
    public void processEmail(EmailMessage message) {
        log.info("Processing email",
            "event_id", message.getEventId(),
            "event_type", message.getEventType(),
            "recipient", message.getRecipientEmail(),
            "priority", message.getPriority()
        );

        try {
            EmailResult result = emailService.sendEmail(message);

            log.info("Email sent successfully",
                "event_id", message.getEventId(),
                "provider", result.getProvider(),
                "provider_message_id", result.getProviderMessageId(),
                "duration_ms", result.getDuration()
            );
        } catch (EmailException e) {
            log.error("Email sending failed",
                "event_id", message.getEventId(),
                "error_type", e.getClass().getSimpleName(),
                "error_message", e.getMessage(),
                "retry_count", message.getRetryCount()
            );
            throw e;
        }
    }
}
```

**Logimustrid:**

```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger_name": "ee.buerokratt.email.worker.EmailWorker",
  "message": "Email sent successfully",
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "event_type": "user_registration",
  "provider": "smtp",
  "provider_message_id": "smtp-id-123",
  "duration_ms": 1234,
  "trace_id": "4bf92f3577b34da6a3ce929d0e0e4736",
  "span_id": "00f067aa0ba902b7"
}
```

### Prometheus Metrics

**Olulised mõõdikud:**

- `email_sent_total` - Kogu saadetud e-kirjade arv (Counter)
- `email_failed_total` - Kogu nurjunud e-kirjade arv (Counter)
- `email_retry_total` - Korduskatsete arv (Counter)
- `email_send_duration_seconds` - Saatmise kestus (Histogram)
- `email_queue_size` - Järjekorra suurus (Gauge)

```java
@Component
public class EmailWorkerMetrics {

    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Histogram durationHistogram;

    public EmailWorkerMetrics(MeterRegistry registry) {
        sentCounter = Counter.builder("email_sent_total")
            .tag("provider", "smtp")
            .register(registry);

        failedCounter = Counter.builder("email_failed_total")
            .tag("error_type", "provider_error")
            .register(registry);

        durationHistogram = Histogram.builder("email_send_duration_seconds")
            .tag("provider", "smtp")
            .register(registry);
    }

    public void recordSuccess(String provider, long duration) {
        Counter.builder("email_sent_total")
            .tag("provider", provider)
            .register(registry)
            .increment();

        durationHistogram.record(duration / 1000.0);
    }
}
```

### Dead Letter Queue Monitoring

**DLQ jälgimine ja häired:**

```yaml
# Prometheus alert rules
- alert: EmailDlqGrowth
  expr: rate(email_dlq_messages_total[10m]) > 10
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Dead letter queue growing rapidly"
    description: "Messages moving to DLQ at high rate: {{ $value }}/min"
```

**DLQ sõnumite taastamine:**

```java
@RestController
@RequestMapping("/admin")
public class AdminController {

    @PostMapping("/dlq/retry/{messageId}")
    public ResponseEntity<?> retryDlqMessage(@PathVariable String messageId) {
        EmailMessage message = dlqService.getMessage(messageId);
        queueService.publishToRetryQueue(message);
        return ResponseEntity.ok().body(Map.of("status", "retrying"));
    }
}
```

### Error Categorization

**Veatüüpide kategoriseerimine:**

- `provider_error` - Email provideri viga (ajutine)
- `validation_error` - Sisendi valideerimise viga (püsiv)
- `template_error` - Malli rendering viga (püsiv)
- `rate_limit_error` - Rate limiit ületatud (ajutine)
- `authentication_error` - Autentimise viga (püsiv)

Ajutised vead põhjustavad korduskatset, püsikvead saadetakse kohe DLQ-sse.

### Health Checks

**Tervisekontrollid Kubernetes'ile:**

```java
@Component
public class HealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        boolean rabbitmqHealthy = rabbitMQHealthCheck();
        boolean databaseHealthy = databaseHealthCheck();
        boolean providerHealthy = emailProvider.isHealthy();

        if (rabbitmqHealthy && databaseHealthy && providerHealthy) {
            return Health.up()
                .withDetail("rabbitmq", "UP")
                .withDetail("database", "UP")
                .withDetail("provider", emailProvider.getProviderName())
                .build();
        } else {
            return Health.down()
                .withDetail("rabbitmq", rabbitmqHealthy ? "UP" : "DOWN")
                .withDetail("database", databaseHealthy ? "UP" : "DOWN")
                .withDetail("provider", providerHealthy ? "UP" : "DOWN")
                .build();
        }
    }
}
```

**Liveness ja Readiness probes:**

- Liveness: `/actuator/health/liveness` - kas pod on elus
- Readiness: `/actuator/health/readiness` - kas pod on võimeline liikluse vastu võtma

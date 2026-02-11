# E-posti Teavitussüsteem - Täielik Lahendus

## Ülevaade

Tootmisvalmis, sündmustepõhine e-posti teavitussüsteem Bürokraadi ökosüsteemile, järgides Bükstack DSL arhitektuuri põhimõtteid.

## Arhitektuuri vastavus

### Bükstack DSL arhitektuuri põhimõtted

**Realisatsioon:**
- Ruuter DSL töövoogude e-kirja orkestreerimiseks (`DSL/Ruuter.private/email/POST/`)
- YAML-põhised töövoogu definitsioonid
- Integratsioon olemasolevate Bükstack komponentidega (TIM, Resql, CronManager)
- REST API otspunktid on avatud Ruuteri kaudu
- Konfiguratsioon konstantide ja keskkonnamuutujate kaudu

**DSL näited:**
- `send-welcome-email.yml` - Kasutaja registreerimise voog
- `send-password-reset.yml` - Parooli lähtestamise voog
- `send-chat-transfer-notification.yml` - VestlusTeavituse voog

### Sündmustepõhine, usaldusväärne, skaleeritav

**Event Bus:** RabbitMQ
- Peamine järjekord: `email.notifications`
- Dead Letter Queue: `email.dlq`
- Retry queue: `email.retry`
- Message TTL: 5 minutit (normaalne), 24 tundi (DLQ)

**Korduskatse poliitikad:**
- Exponential backoff: 2^n sekundit (maks 60s)
- Prioriteedipõhised piirangud:
  - Critical: 5 korduskatset
  - High: 3 korduskatset
  - Normal: 2 korduskatset
  - Low: 1 korduskatse

**Järjekorra konfiguratsioon:**
- Püsivad järjekorrad
- Püsivad sõnumid
- Prioriteetsete järjekorrade tugi
- Publisher confirms lubatud

**Skaleeritavus:**
- Horisontaalne skaleerimine Kubernetes HPA kaudu (3-10 replikat)
- Worker pool: 5-20 üheaegselt tarbijat
- Connection pooling
- Asünkroonne töötlus

### Logimine ja veajälgimine

**Struktureeritud logimine:**
- JSON formaadi logid OpenSearchis
- Sündmuste jälgimine päringust kohaletoimetamiseni
* Veadetailid koos stack trace'idega
- Jõudlusmõõdikud kaasatud

**Distributed tracing:**
- OpenTelemetry integratsioon
- Trace ID edastus
- Päringu korrelatsioon
- Jõudlusanalüüs

**Mõõdikud (Prometheus):**
- `email_sent_total` - Saadetud e-kirjad kokku
- `email_failed_total` - Nurjunud kokku
- `email_retry_total` - Korduskatsed
- `email_send_duration_seconds` - Saatmise kestus (p50, p95, p99)
- `email_queue_size` - Järjekorra sügavus

**Veajälgimine:**
- DLQ monitooring
- Nurjunud sõnumite detailid
- Vea kategoriseerimine
- Korduskatsete jälgimine

### Ökosüsteemi integratsioon

**TIM integratsioon:**
- JWT autentimine ja valideerimine
- Tokeni verifitseerimine JWKS otspunkti kaudu
- Scope-põhine autoriseerimine
- Turvakonfiguratsioon

**Resql integratsioon:**
- E-kirja kohaletoimetamise jälgimise tabel
- Mallide salvestamine
- Kasutajaeelistused
- Salvestatud protseduurid logimiseks

**CronManager integratsioon:**
- Retry queue töötlus (iga 5 min)
- Vanade kirjete puhastus (iga päev kell 2 AM)
- Igapäevase kokkuvõtte genereerimine (iga päev kell 8 AM)
- DLQ monitooring (iga 10 min)
- Malli vahemälu värskendus (iga tund)

**Ruuter integratsioon:**
- DSL töövoogud e-kirja päästikuteks
- HTTP POST e-posti teenusele
- Vastuse käitlemine
- Vea edastus

### Koodi realisatsioon

**E-posti teenusepakkujad:**
1. **SMTP** (`SmtpEmailProvider.java`)
   - JavaMail API
   - StartTLS tugi
   - Connection pooling
   - Timeout konfiguratsioon

2. **AWS SES** (Arhitektuur defineeritud)
   - AWS SDK v2
   - Signature V4
   - Region konfiguratsioon

3. **SendGrid** (Arhitektuur defineeritud)
   - SendGrid Java SDK
   - API võtme autentimine
   - Mallide tugi

**Koodi kvaliteet:**
- Java 17 koos Spring Boot 3.x
- Lombok vähema boilerplate'i jaoks
- Dependency injection
- Konfiguratsiooni välisistamine
- Erandite käitlemine
- Sisendi valideerimine (Jakarta Validation)
- Ressursside puhastamine
- Thread safety

## Kataloogi struktuur

```
Email-Notification-System/
├── README.md                    # Main documentation
├── pom.xml                      # Maven configuration
├── Dockerfile                   # Container image
├── docker-compose.yml           # Local development
├── database/
│   └── schema.sql               # PostgreSQL schema
├── docs/
│   ├── ARCHITECTURE.md          # Detailed architecture
│   ├── API.md                   # REST API reference
│   ├── MONITORING.md            # Monitoring guide
│   └── DEPLOYMENT.md            # Deployment guide
├── helm-chart/                  # Kubernetes Helm chart
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       └── deployment.yaml
├── DSL/
│   ├── Ruuter.private/email/POST/
│   │   ├── send-welcome-email.yml
│   │   ├── send-password-reset.yml
│   │   └── send-chat-transfer-notification.yml
│   └── CronManager/
│       └── email-notifications.yml
└── src/main/
    ├── java/ee/buerokratt/email/
    │   ├── EmailNotificationApplication.java
    │   ├── config/
    │   │   ├── RabbitMQConfig.java
    │   │   ├── EmailProviderConfig.java
    │   │   ├── SecurityConfig.java
    │   │   └── PrometheusConfig.java
    │   ├── controller/
    │   │   └── EmailController.java
    │   ├── service/
    │   │   ├── EmailService.java
    │   │   ├── QueueService.java
    │   │   ├── TemplateService.java
    │   │   └── provider/
    │   │       ├── EmailProvider.java
    │   │       ├── EmailException.java
    │   │       └── SmtpEmailProvider.java
    │   ├── model/
    │   │   ├── EmailRequest.java
    │   │   ├── EmailMessage.java
    │   │   ├── EmailResult.java
    │   │   └── DeliveryStatus.java
    │   └── worker/
    │       ├── EmailWorker.java
    │       └── EmailWorkerMetrics.java
    └── resources/
        ├── application.yml
        └── application-dev.yml
```

## Põhivõimalused

### Funktsionaalsed võimalused
- Multiple email providers (SMTP, SES, SendGrid)
- Template engine (Handlebars)
- Multi-language support (et, en, ru)
- Batch email sending
- Priority queues
- Scheduled sending
- Idempotency checks
- Email preferences
- Delivery tracking
- Status API

### Mittefunktsionaalsed võimalused
- JWT authentication
- Rate limiting
- Input validation
- Error handling
- Retry logic
- Dead letter queue
- Monitoring metrics
- Distributed tracing
- Structured logging
- Health checks
- Graceful shutdown
- Horizontal scaling

## API otspunktid

| Method | Endpoint                     | Description                |
|--------|------------------------------|----------------------------|
| POST   | /email/send                  | Send single email          |
| POST   | /email/send-batch            | Send batch emails          |
| GET    | /email/status/{messageId}    | Get delivery status        |
| POST   | /email/retry/{messageId}     | Retry failed email         |
| DELETE | /email/{messageId}           | Cancel scheduled email     |
| GET    | /email/health                | Health check               |
| GET    | /actuator/prometheus         | Prometheus metrics         |
| GET    | /actuator/health             | Detailed health            |

## Toetatud sündmuste tüübid

| Event Type          | Template                    | Priority | Use Case                    |
|---------------------|-----------------------------|----------|-----------------------------|
| user_registration   | welcome-email               | normal   | New user registration        |
| password_reset      | password-reset              | high     | Password reset requests      |
| chat_transferred    | chat-transfer-notification  | normal   | Chat transfer notifications  |
| system_alert        | system-alert                | critical | System alerts               |
| daily_digest        | daily-summary               | low      | Daily digest emails         |

## Konfiguratsiooni näited

### Application Properties
```yaml
email:
  provider: smtp
  from: noreply@buerokratt.ee
  smtp:
    host: smtp.example.com
    port: 587
    auth: true
    starttls: true

rabbitmq:
  host: rabbitmq
  port: 5672
  username: guest
  password: guest

tim:
  url: http://byk-tim

resql:
  url: http://byk-resql
```

### Helm Values
```yaml
replicaCount: 3
image:
  repository: buerokratt/email-notification-service
  tag: "1.0.0"

autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
```

## Monitoeringu seadistamine

### Prometheus häired
- High failure rate (>10%)
- Service down
- Queue backlog (>1000)
- DLQ growth
- High latency (p95 > 10s)

### Grafana armatuurlaud
- Emails sent per second
- Success rate percentage
- Queue size over time
- Send duration (p95)
- Failures by event type
- Retry rate

### Logimustri mustrid
```json
{
  "event_type": "email_sent",
  "event_id": "550e8400-...",
  "provider": "smtp",
  "duration_ms": 1234,
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

## Juurutamise sammud

1. **Eeltingimused**
   - Kubernetes cluster
   - PostgreSQL database
   - RabbitMQ instance
   - Redis cache

2. **Andmebaasi seadistamine**
   ```bash
   psql -f database/schema.sql
   ```

3. **Create Secrets**
   ```bash
   kubectl create secret generic email-secrets \
     --from-literal=smtp-password=xxx
   ```

4. **Install Helm Chart**
   ```bash
   helm install email-notification ./helm-chart
   ```

5. **Verify**
   ```bash
   kubectl get pods -l app=email-notification-service
   curl http://email-service/actuator/health
   ```

## Testimine

### Käsitsi testimine
```bash
# Send test email
curl -X POST http://localhost:8085/email/send \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "user_registration",
    "recipientEmail": "test@example.com",
    "templateId": "welcome-email"
  }'
```

### Koormusetestimine
```bash
# Gatling tests
mvn gatling:test
```

## Jõudlus eesmärgid

| Metric              | Target | Alert          |
|---------------------|--------|----------------|
| API Response        | <100ms | >500ms         |
| Queue Processing    | <5s    | >30s           |
| Email Send          | <10s   | >30s           |
| Template Render     | <50ms  | >200ms         |
| Success Rate        | >99%   | <95%           |
| Failure Rate        | <1%    | >10%           |


- Documentation: See `docs/` directory
- API Reference: `docs/API.md`
- Architecture: `docs/ARCHITECTURE.md`
- Deployment: `docs/DEPLOYMENT.md`
- Monitoring: `docs/MONITORING.md`

---


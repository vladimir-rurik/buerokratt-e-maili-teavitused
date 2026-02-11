# Email Notification System - Complete Solution

## Overview

A production-ready, event-driven email notification system for the Bürokraatt ecosystem following Bükstack DSL architecture principles.

## Architecture Compliance

### Bükstack DSL Architecture Principles

**Implementation:**
- Ruuter DSL workflows for email orchestration (`DSL/Ruuter.private/email/POST/`)
- YAML-based workflow definitions
- Integration with existing Bükstack components (TIM, Resql, CronManager)
- REST API endpoints exposed via Ruuter
- Configuration via constants and environment variables

**DSL Examples:**
- `send-welcome-email.yml` - User registration flow
- `send-password-reset.yml` - Password reset flow
- `send-chat-transfer-notification.yml` - Chat notification flow

### Event-Driven, Reliable, Scalable

**Event Bus:** RabbitMQ
- Primary queue: `email.notifications`
- Dead Letter Queue: `email.dlq`
- Retry queue: `email.retry`
- Message TTL: 5 minutes (normal), 24 hours (DLQ)

**Retry Policies:**
- Exponential backoff: 2^n seconds (max 60s)
- Priority-based limits:
  - Critical: 5 retries
  - High: 3 retries
  - Normal: 2 retries
  - Low: 1 retry

**Queue Configuration:**
- Durable queues
- Persistent messages
- Priority queues supported
- Publisher confirms enabled

**Scalability:**
- Horizontal scaling via Kubernetes HPA (3-10 replicas)
- Worker pool: 5-20 concurrent consumers
- Connection pooling
- Async processing

### Logging and Error Tracking

**Structured Logging:**
- JSON format logs to OpenSearch
- Event tracking from request to delivery
- Error details with stack traces
- Performance metrics included

**Distributed Tracing:**
- OpenTelemetry integration
- Trace ID propagation
- Request correlation
- Performance analysis

**Metrics (Prometheus):**
- `email_sent_total` - Total sent emails
- `email_failed_total` - Total failures
- `email_retry_total` - Retry attempts
- `email_send_duration_seconds` - Send duration (p50, p95, p99)
- `email_queue_size` - Queue depth

**Error Tracking:**
- DLQ monitoring
- Failed message details
- Error categorization
- Retry tracking

### Ecosystem Integration

**TIM Integration:**
- JWT authentication and validation
- Token verification via JWKS endpoint
- Scope-based authorization
- Security configuration

**Resql Integration:**
- Email delivery tracking table
- Template storage
- User preferences
- Stored procedures for logging

**CronManager Integration:**
- Retry queue processing (every 5 min)
- Old record cleanup (daily at 2 AM)
- Daily digest generation (daily at 8 AM)
- DLQ monitoring (every 10 min)
- Template cache refresh (hourly)

**Ruuter Integration:**
- DSL workflows for email triggers
- HTTP POST to email service
- Response handling
- Error propagation

### Code Implementation

**Email Providers:**
1. **SMTP** (`SmtpEmailProvider.java`)
   - JavaMail API
   - StartTLS support
   - Connection pooling
   - Timeout configuration

2. **AWS SES** (Architecture defined)
   - AWS SDK v2
   - Signature V4
   - Region configuration

3. **SendGrid** (Architecture defined)
   - SendGrid Java SDK
   - API key authentication
   - Template support

**Code Quality:**
- Java 17 with Spring Boot 3.x
- Lombok for reduced boilerplate
- Dependency injection
- Configuration externalization
- Exception handling
- Input validation (Jakarta Validation)
- Resource cleanup
- Thread safety

## Directory Structure

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

## Key Features

### Functional Features
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

### Non-Functional Features
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

## API Endpoints

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

## Event Types Supported

| Event Type          | Template                    | Priority | Use Case                    |
|---------------------|-----------------------------|----------|-----------------------------|
| user_registration   | welcome-email               | normal   | New user registration        |
| password_reset      | password-reset              | high     | Password reset requests      |
| chat_transferred    | chat-transfer-notification  | normal   | Chat transfer notifications  |
| system_alert        | system-alert                | critical | System alerts               |
| daily_digest        | daily-summary               | low      | Daily digest emails         |

## Configuration Examples

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

## Monitoring Setup

### Prometheus Alerts
- High failure rate (>10%)
- Service down
- Queue backlog (>1000)
- DLQ growth
- High latency (p95 > 10s)

### Grafana Dashboard
- Emails sent per second
- Success rate percentage
- Queue size over time
- Send duration (p95)
- Failures by event type
- Retry rate

### Log Patterns
```json
{
  "event_type": "email_sent",
  "event_id": "550e8400-...",
  "provider": "smtp",
  "duration_ms": 1234,
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

## Deployment Steps

1. **Prerequisites**
   - Kubernetes cluster
   - PostgreSQL database
   - RabbitMQ instance
   - Redis cache

2. **Database Setup**
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

## Testing

### Manual Testing
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

### Load Testing
```bash
# Gatling tests
mvn gatling:test
```

## Performance Targets

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


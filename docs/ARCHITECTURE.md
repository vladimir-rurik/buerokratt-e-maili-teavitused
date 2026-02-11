# Email Notification System Architecture

## System Overview

The Email Notification System is designed to provide a reliable, scalable, and event-driven email delivery service for the Bürokraatt ecosystem. It follows the Bükstack DSL architecture principles and integrates seamlessly with existing services.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Bürokraat Ecosystem                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐   │
│  │   Ruuter     │─────▶│   Ruuter     │─────▶│   Ruuter     │   │
│  │   (Public)   │      │   (Private)  │      │  (Internal)  │   │
│  └──────────────┘      └──────────────┘      └──────────────┘   │
│         │                      │                      │         │
│         └──────────────────────┴──────────────────────┘         │
│                                ▼                                │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              Email Notification Service                 │    │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐         │    │
│  │  │   DSL      │  │   Queue    │  │  Worker    │         │    │
│  │  │  Files     │  │  Manager   │  │  Pool      │         │    │
│  │  └────────────┘  └────────────┘  └────────────┘         │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                │                                │
│                                ▼                                │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    RabbitMQ                             │    │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │    │
│  │  │ Email Queue  │  │    DLQ       │  │  Retry Queue │   │    │
│  │  └──────────────┘  └──────────────┘  └──────────────┘   │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                │                                │
│                                ▼                                │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │            Email Provider (SMTP/SES/SendGrid)           │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │    Resql     │  │    TIM       │  │ CronManager  │           │
│  └──────────────┘  └──────────────┘  └──────────────┘           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Email Notification Service

**Technology:** Java 17, Spring Boot 3.x

**Responsibilities:**
- Accept email requests via REST API
- Validate and enrich email data
- Render email templates
- Publish messages to RabbitMQ
- Process emails from queues
- Track delivery status
- Expose Prometheus metrics

**Key Features:**
- JWT authentication via TIM
- Rate limiting
- Idempotency checks
- Template caching
- Distributed tracing

### 2. RabbitMQ Message Queues

**Queues:**
- **email.notifications** - Primary queue for email processing
- **email.dlq** - Dead Letter Queue for failed messages
- **email.retry** - Retry queue with exponential backoff

**Features:**
- Message TTL (Time To Live)
- Priority queues
- Persistent messages
- Automatic retries
- Dead letter handling

### 3. Email Providers

**Supported Providers:**
- **SMTP** - Standard SMTP servers
- **AWS SES** - Amazon Simple Email Service
- **SendGrid** - SendGrid API

**Provider Abstraction:**
```java
public interface EmailProvider {
    EmailResult send(EmailMessage message) throws EmailException;
    String getProviderName();
    boolean isHealthy();
}
```

### 4. Ruuter DSL Integration

**Workflow Files:**
- `send-welcome-email.yml` - New user registration
- `send-password-reset.yml` - Password reset requests
- `send-chat-transfer-notification.yml` - Chat transfers

**Example Workflow:**
```yaml
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

### 5. Database Schema (Resql Integration)

**Tables:**
- `email_deliveries` - Track all email delivery attempts
- `email_templates` - Store email templates
- `email_preferences` - User email preferences
- `email_delivery_log` - Analytics log

### 6. Monitoring & Observability

**Metrics (Prometheus):**
- `email_sent_total` - Total emails sent
- `email_failed_total` - Total failures
- `email_retry_total` - Retry attempts
- `email_send_duration_seconds` - Send duration
- `email_queue_size` - Queue depth

**Logging (OpenSearch):**
- Structured JSON logs
- Event tracking
- Error details
- Performance metrics

**Tracing (OpenTelemetry):**
- Distributed tracing across services
- Request correlation
- Performance analysis

## Data Flow

### Normal Flow

1. **Event Triggered** → Business event occurs
2. **Ruuter DSL** → Workflow starts
3. **Email API** → POST /email/send
4. **Validation** → Request validated
5. **Template Render** → Email content generated
6. **Queue Publish** → Message sent to RabbitMQ
7. **Worker Process** → Consumer picks up message
8. **Provider Send** → Email sent via provider
9. **Status Update** → Database updated
10. **Log & Metric** → Monitoring updated

### Failure Flow

1. **Send Fails** → Provider error
2. **Retry Check** → Check retry count
3. **Below Limit** → Send to retry queue with backoff
4. **Above Limit** → Send to DLQ
5. **Alert** → Prometheus alert triggered
6. **Manual Review** → DLQ messages reviewed
7. **Retry/Delete** → Manual intervention

## Retry Strategy

### Exponential Backoff

```
Retry 1: 2 seconds
Retry 2: 4 seconds
Retry 3: 8 seconds
Retry 4: 16 seconds
Retry 5: 32 seconds
Maximum: 60 seconds
```

### Priority-Based Limits

| Priority   | Max Retries |
|------------|-------------|
| Critical   | 5           |
| High       | 3           |
| Normal     | 2           |
| Low        | 1           |

## Security

### Authentication
- JWT tokens via TIM
- Token validation on all endpoints
- Scope-based authorization

### Authorization
- `SCOPE_email:send` - Send emails
- `SCOPE_email:send_batch` - Batch operations
- `SCOPE_email:read` - Check status
- `SCOPE_email:retry` - Retry failed emails
- `SCOPE_email:cancel` - Cancel scheduled emails

### Rate Limiting
- Per API key limits
- Configurable requests per minute
- Redis-backed distributed limiting

### Input Validation
- Email format validation
- Template injection prevention
- XSS protection via sanitization

## Scaling

### Horizontal Scaling

**Kubernetes HPA:**
```yaml
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80
```

**Worker Scaling:**
```java
@RabbitListener(
    queues = "email.notifications",
    concurrency = "5-20"  // Min 5, Max 20 consumers
)
```

### Vertical Scaling

**Resource Limits:**
```yaml
resources:
  limits:
    cpu: 1000m
    memory: 1Gi
  requests:
    cpu: 250m
    memory: 512Mi
```

## Performance Considerations

### Optimization Strategies

1. **Template Caching** - Templates cached in Redis
2. **Connection Pooling** - Reused SMTP/HTTP connections
3. **Batch Processing** - Parallel email sending
4. **Async Processing** - Non-blocking queue operations
5. **Database Indexing** - Optimized queries

### Performance Targets

| Metric              | Target        |
|---------------------|---------------|
| API Response Time   | < 100ms       |
| Queue Processing    | < 5 seconds   |
| Email Send Time     | < 10 seconds  |
| Template Render     | < 50ms        |
| Database Query      | < 100ms       |

## Reliability Features

### High Availability
- Multiple service replicas
- RabbitMQ clustering
- Database failover
- Health checks and probes

### Fault Tolerance
- Circuit breakers
- Retry policies
- Dead letter queues
- Graceful degradation

### Data Integrity
- Idempotency keys
- Transactional operations
- Audit logging
- Status tracking

## Integration Points

### TIM Integration
```
Request → JWT Validation → TIM Service → User Info → Email Service
```

### Resql Integration
```
Email Service → Resql → PostgreSQL → Delivery Status
```

### CronManager Integration
```
CronManager → Scheduled Job → Email API → Batch Processing
```

### Ruuter Integration
```
Ruuter DSL → HTTP Call → Email API → Queue → Provider
```

## Deployment Architecture

### Kubernetes Deployment

```
┌─────────────────────────────────────┐
│        Kubernetes Cluster           │
├─────────────────────────────────────┤
│                                     │
│  ┌──────────┐  ┌──────────┐         │
│  │   Pod 1  │  │   Pod 2  │  ...    │
│  │  (Email  │  │  (Email  │         │
│  │ Service) │  │ Service) │         │
│  └──────────┘  └──────────┘         │
│         │             │             │
│         └──────┬──────┘             │
│                ▼                    │
│        ┌───────────┐                │
│        │ Service   │                │
│        └───────────┘                │
│                │                    │
│         ┌──────┴──────┐             │
│         ▼             ▼             │
│  ┌──────────┐  ┌──────────┐         │
│  │ Ingress  │  │Prometheus│         │
│  └──────────┘  └──────────┘         │
└─────────────────────────────────────┘
```

## Monitoring Strategy

### Alert Rules

1. **High Failure Rate** - > 10% failure rate for 5 minutes
2. **Queue Backlog** - > 1000 messages in queue
3. **DLQ Growth** - Rapid DLQ message growth
4. **Service Down** - Service not responding
5. **High Latency** - P95 latency > 10 seconds

### Dashboards

- Overview dashboard
- Performance metrics
- Error analysis
- Queue monitoring
- Provider status



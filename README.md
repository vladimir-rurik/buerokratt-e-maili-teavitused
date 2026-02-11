# Email Notification System for Bürokraat

## Architecture

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
└─────────────────────────────────────────────────────────────────┘
```

## Features

- **Event-driven architecture** using RabbitMQ message queues
- **Retry policies** with exponential backoff
- **Dead Letter Queue (DLQ)** for failed messages
- **Multiple email providers**: SMTP, AWS SES, SendGrid
- **Bükstack DSL integration** via Ruuter workflows
- **Prometheus monitoring** and alerting
- **Distributed tracing** with OpenTelemetry
- **Structured logging** to OpenSearch
- **JWT authentication** via TIM
- **Kubernetes deployment** with Helm charts
- **Priority queues** for critical notifications

## Technology Stack

- **Java 17** with Spring Boot 3.x
- **RabbitMQ** for message queuing
- **Prometheus** for metrics
- **OpenTelemetry** for distributed tracing
- **OpenSearch** for logging
- **TIM** for authentication
- **Resql** for data persistence
- **Kubernetes** with Helm for deployment

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Java 17
- Maven 3.x
- Access to RabbitMQ, TIM, Resql, OpenSearch

### Local Development

```bash
# Clone the repository
cd /Users/rurik/buerokratt/Email-Notification-System

# Build the application
mvn clean package

# Run with Docker Compose
docker-compose up -d

# Or run locally
mvn spring-boot:run
```

### Configuration

Edit `src/main/resources/application.yml`:

```yaml
email:
  provider: smtp  # smtp, ses, sendgrid
  from: noreply@buerokratt.ee

rabbitmq:
  host: localhost
  port: 5672
  username: guest
  password: guest
```

## Directory Structure

```
Email-Notification-System/
├── src/main/java/ee/buerokratt/email/
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
│   │       ├── SmtpEmailProvider.java
│   │       ├── SesEmailProvider.java
│   │       └── SendGridEmailProvider.java
│   ├── model/
│   │   ├── EmailRequest.java
│   │   ├── EmailMessage.java
│   │   ├── EmailResult.java
│   │   └── DeliveryStatus.java
│   ├── worker/
│   │   └── EmailWorker.java
│   └── validator/
│       └── EmailValidator.java
├── src/main/resources/
│   ├── application.yml
│   └── application-dev.yml
├── DSL/
│   ├── Ruuter.private/email/POST/
│   │   ├── send-welcome-email.yml
│   │   ├── send-password-reset.yml
│   │   └── send-chat-transfer-notification.yml
│   └── CronManager/
│       └── email-notifications.yml
├── helm-chart/
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
│       ├── deployment.yaml
│       ├── service.yaml
│       └── ingress.yaml
├── docs/
│   ├── ARCHITECTURE.md
│   ├── API.md
│   ├── MONITORING.md
│   └── DEPLOYMENT.md
├── database/
│   └── schema.sql
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

## Usage

### Sending an Email via API

```bash
curl -X POST http://localhost:8085/email/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "eventId": "unique-event-id",
    "eventType": "user_registration",
    "recipientEmail": "user@example.com",
    "recipientName": "John Doe",
    "templateId": "welcome-email",
    "priority": "normal",
    "locale": "et",
    "templateData": {
      "name": "John Doe",
      "confirmationUrl": "https://example.com/confirm"
    }
  }'
```

### Sending via Ruuter DSL

See `DSL/Ruuter.private/email/POST/` for workflow examples.

## Event Types

| Event Type | Template | Priority | Description |
|------------|----------|----------|-------------|
| `user_registration` | welcome-email | normal | New user registration |
| `password_reset` | password-reset | high | Password reset request |
| `chat_transferred` | chat-transfer-notification | normal | Chat transferred to human agent |
| `system_alert` | system-alert | critical | System alert notifications |
| `daily_digest` | daily-summary | low | Daily digest emails |

## Monitoring

### Metrics Endpoint

```
http://localhost:8085/actuator/prometheus
```

### Key Metrics

- `email_sent_total` - Total emails sent
- `email_failed_total` - Total failed emails
- `email_retry_total` - Total retry attempts
- `email_send_duration_seconds` - Email send duration
- `email_queue_size` - Current queue size

### Health Checks

```
http://localhost:8085/actuator/health
```

## Deployment

### Kubernetes with Helm

```bash
# Install Helm chart
helm install email-notification ./helm-chart \
  --namespace buerokratt \
  --values helm-chart/values.yaml

# Upgrade deployment
helm upgrade email-notification ./helm-chart \
  --namespace buerokratt \
  --values helm-chart/values.yaml

# Uninstall
helm uninstall email-notification --namespace buerokratt
```

### Environment Variables

See `docs/DEPLOYMENT.md` for complete environment variable reference.

## Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Load tests
mvn gatling:test
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md) - Detailed system architecture
- [API Reference](docs/API.md) - REST API documentation
- [Monitoring](docs/MONITORING.md) - Monitoring and alerting setup
- [Deployment](docs/DEPLOYMENT.md) - Deployment guide

## Security

- JWT authentication via TIM
- Rate limiting per API key
- Input validation and sanitization
- TLS encryption for all communications
- Secrets management via Kubernetes Secrets

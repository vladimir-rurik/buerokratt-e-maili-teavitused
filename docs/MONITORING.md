# Monitooringu ja Häirete Juhend

## Metrics Endpoint

```
http://localhost:8085/actuator/prometheus
```

## Key Metrics

### Counter Metrics

#### email_sent_total
Edukalt saadetud e-kirjade koguarv.

**Labels:**
- `application` - Application name
- `provider` - Email provider (smtp, ses, sendgrid)
- `event_type` - Event type

**Example:**
```
email_sent_total{application="email-notification-service",provider="smtp",event_type="user_registration"} 1234.0
```

#### email_failed_total
Nurjunud e-kirjade koguarv.

**Labels:**
- `application` - Application name
- `event_type` - Event type
- `error_type` - Error type

**Example:**
```
email_failed_total{application="email-notification-service",event_type="user_registration",error_type="provider_error"} 12.0
```

#### email_retry_total
E-kirja korduskatsete koguarv.

**Labels:**
- `application` - Application name
- `event_type` - Event type

**Example:**
```
email_retry_total{application="email-notification-service",event_type="user_registration"} 45.0
```

### Gauge Metrics

#### email_queue_size
E-kirja töötlemisjärjekorra praegune suurus.

**Labels:**
- `application` - Application name

**Example:**
```
email_queue_size{application="email-notification-service"} 234.0
```

### Histogram Metrics

#### email_send_duration_seconds
E-kirja saatmise kestus sekundites.

**Labels:**
- `application` - Application name
- `provider` - Email provider

**Quantiles:**
- p50 - Median
- p95 - 95th percentile
- p99 - 99th percentile

**Example:**
```
email_send_duration_seconds_bucket{application="email-notification-service",provider="smtp",le="0.1"} 800.0
email_send_duration_seconds_bucket{application="email-notification-service",provider="smtp",le="0.5"} 950.0
email_send_duration_seconds_bucket{application="email-notification-service",provider="smtp",le="+Inf"} 1000.0
```

## Prometheus Queries

### Success Rate

```promql
sum(rate(email_sent_total[5m])) / (sum(rate(email_sent_total[5m])) + sum(rate(email_failed_total[5m]))) * 100
```

### Failure Rate by Event Type

```promql
sum by (event_type) (rate(email_failed_total[5m]))
```

### Average Send Duration

```promql
histogram_quantile(0.95, rate(email_send_duration_seconds_bucket[5m]))
```

### Queue Growth Rate

```promql
deriv(email_queue_size[5m])
```

### Retry Rate

```promql
sum(rate(email_retry_total[5m])) / sum(rate(email_sent_total[5m]))
```

## Alert Rules

### Critical Alerts

#### High Failure Rate
```yaml
- alert: EmailHighFailureRate
  expr: |
    sum(rate(email_failed_total[5m])) /
    (sum(rate(email_sent_total[5m])) + sum(rate(email_failed_total[5m]))) > 0.1
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "High email failure rate detected"
    description: "Email failure rate is above 10% for 5 minutes (current: {{ $value | humanizePercentage }})"
```

#### Service Down
```yaml
- alert: EmailNotificationServiceDown
  expr: up{job="email-notification-service"} == 0
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "Email notification service is down"
    description: "Service has been down for 2 minutes"
```

#### Dead Letter Queue Growing
```yaml
- alert: EmailDlqGrowth
  expr: rate(email_dlq_messages_total[10m]) > 10
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Dead letter queue growing rapidly"
    description: "Messages moving to DLQ at high rate: {{ $value }}/min"
```

### Warning Alerts

#### Queue Backlog
```yaml
- alert: EmailQueueBacklog
  expr: email_queue_size > 1000
  for: 10m
  labels:
    severity: warning
  annotations:
    summary: "Email queue backlog detected"
    description: "{{ $value }} messages waiting in queue"
```

#### High Latency
```yaml
- alert: EmailSendLatencyHigh
  expr: |
    histogram_quantile(0.95,
      rate(email_send_duration_seconds_bucket[5m])) > 10
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High email send latency"
    description: "95th percentile latency is over 10 seconds (current: {{ $value }}s)"
```

#### Low Success Rate
```yaml
- alert: EmailLowSuccessRate
  expr: |
    sum(rate(email_sent_total[15m])) /
    (sum(rate(email_sent_total[15m])) + sum(rate(email_failed_total[15m]))) < 0.95
  for: 15m
  labels:
    severity: warning
  annotations:
    summary: "Low email success rate"
    description: "Success rate below 95% for 15 minutes (current: {{ $value | humanizePercentage }})"
```

## Grafana Dashboards

### Dashboard JSON

Impordi see JSON Grafanasse:

```json
{
  "dashboard": {
    "title": "Email Notification Service",
    "tags": ["email", "notifications"],
    "timezone": "browser",
    "panels": [
      {
        "title": "Emails Sent",
        "type": "graph",
        "targets": [
          {
            "expr": "sum(rate(email_sent_total[5m]))",
            "legendFormat": "Sent/sec"
          }
        ]
      },
      {
        "title": "Success Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "sum(rate(email_sent_total[5m])) / (sum(rate(email_sent_total[5m])) + sum(rate(email_failed_total[5m]))) * 100",
            "legendFormat": "Success %"
          }
        ]
      },
      {
        "title": "Queue Size",
        "type": "graph",
        "targets": [
          {
            "expr": "email_queue_size",
            "legendFormat": "Queue Size"
          }
        ]
      },
      {
        "title": "Send Duration (p95)",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(email_send_duration_seconds_bucket[5m]))",
            "legendFormat": "p95 Duration"
          }
        ]
      },
      {
        "title": "Failures by Event Type",
        "type": "graph",
        "targets": [
          {
            "expr": "sum by (event_type) (rate(email_failed_total[5m]))",
            "legendFormat": "{{event_type}}"
          }
        ]
      },
      {
        "title": "Retry Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "sum(rate(email_retry_total[5m]))",
            "legendFormat": "Retries/sec"
          }
        ]
      }
    ]
  }
}
```

## Log Monitoring

### Log Levels

- **ERROR** - Errors requiring attention
- **WARN** - Warning messages
- **INFO** - Informational messages
- **DEBUG** - Debug messages (development only)

### Log Patterns

#### Success Pattern
```json
{
  "event_type": "email_sent",
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "provider": "smtp",
  "provider_message_id": "smtp-id-123",
  "duration_ms": 1234,
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

#### Failure Pattern
```json
{
  "event_type": "email_failed",
  "event_id": "550e8400-e29b-41d4-a716-446655440000",
  "error": "Connection refused",
  "retry_count": 2,
  "priority": "normal",
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

### OpenSearch Queries

#### Find Failed Emails
```json
GET email-notifications/_search
{
  "query": {
    "match": {
      "event_type": "email_failed"
    }
  },
  "sort": [
    { "@timestamp": "desc" }
  ]
}
```

#### Errors by Provider
```json
GET email-notifications/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "event_type": "email_failed" } }
      ]
    }
  },
  "aggs": {
    "by_provider": {
      "terms": {
        "field": "provider"
      }
    }
  }
}
```

## Health Checks

### Liveness Probe
```
GET /actuator/health/liveness
```

Expected response:
```json
{
  "status": "UP"
}
```

### Readiness Probe
```
GET /actuator/health/readiness
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "rabbitmq": { "status": "UP" },
    "database": { "status": "UP" }
  }
}
```

## Distributed Tracing

### OpenTelemetry Setup

The service uses OpenTelemetry for distributed tracing. Traces are automatically exported to Jaeger or Zipkin.

### Trace Headers

```
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

### Viewing Traces

Access Jaeger UI:
```
http://jaeger:16686
```

Search for:
- Service: `email-notification-service`
- Operation: `/email/send`
- Tags: `event_id`, `event_type`

## Performance Monitoring

### Response Time Targets

| Operation           | Target | Alert Threshold |
|---------------------|--------|-----------------|
| POST /email/send    | 100ms  | 500ms           |
| GET /email/status   | 50ms   | 200ms           |
| Queue processing    | 5s     | 30s             |
| Template render     | 50ms   | 200ms           |

### Throughput Metrics

| Metric                     | Target | Alert Threshold |
|----------------------------|--------|-----------------|
| Emails per second          | 100    | N/A             |
| Queue processing rate      | 100/s  | < 50/s          |
| Success rate               | 99%    | < 95%           |
| Retry rate                 | < 5%   | > 10%           |

## Setup Instructions

### Prometheus Configuration

Add to `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'email-notification-service'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - buerokratt
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: email-notification-service
      - source_labels: [__meta_kubernetes_pod_ip]
        target_label: __address__
        replacement: $1:8085
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod
```

### AlertManager Configuration

Add to `alertmanager.yml`:

```yaml
route:
  group_by: ['alertname', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'

receivers:
  - name: 'default'
    email_configs:
      - to: 'alerts@buerokratt.ee'
        from: 'alertmanager@buerokratt.ee'
        smarthost: 'smtp.example.com:587'
```

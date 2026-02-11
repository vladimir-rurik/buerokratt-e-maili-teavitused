# Juurutamise Juhend

## Prerequisites

- Kubernetes 1.24+
- Helm 3.x
- PostgreSQL database
- RabbitMQ 3.12+
- Redis 6+
- Existing Bürokraatt infrastructure (TIM, Resql, Ruuter)

## Quick Start

### 1. Prepare Secrets

Create Kubernetes secrets:

```bash
kubectl create secret generic email-secrets \
  --from-literal=smtp-host=smtp.example.com \
  --from-literal=smtp-port=587 \
  --from-literal=smtp-username=user@example.com \
  --from-literal=smtp-password=your-password \
  --namespace buerokratt

kubectl create secret generic rabbitmq-secret \
  --from-literal=password=your-rabbitmq-password \
  --namespace buerokratt
```

### 2. Install Helm Chart

```bash
helm repo add buerokratt https://charts.buerokratt.ee
helm repo update

helm install email-notification ./helm-chart \
  --namespace buerokratt \
  --values helm-chart/values.yaml \
  --set email.smtp.host=smtp.example.com \
  --set email.smtp.username=user@example.com \
  --set image.tag=1.0.0
```

### 3. Verify Installation

```bash
# Check pods
kubectl get pods -n buerokratt -l app=email-notification-service

# Check service
kubectl get svc email-notification-service -n buerokratt

# Check logs
kubectl logs -n buerokratt -l app=email-notification-service --tail=100

# Test health endpoint
kubectl run test-pod --image=curlimages/curl -i --rm --restart=Never -- \
  curl http://email-notification-service.buerokratt.svc.cluster.local:8085/email/health
```

## Configuration

### Environment Variables

| Variable                  | Description                         | Default                |
|---------------------------|-------------------------------------|------------------------|
| EMAIL_PROVIDER            | Email provider (smtp, ses, sendgrid) | smtp                   |
| EMAIL_FROM                | From email address                   | noreply@buerokratt.ee  |
| SMTP_HOST                 | SMTP server host                     | smtp.example.com       |
| SMTP_PORT                 | SMTP server port                     | 587                    |
| RABBITMQ_HOST             | RabbitMQ host                       | rabbitmq               |
| TIM_URL                   | TIM service URL                     | http://byk-tim         |
| RESQL_URL                 | Resql service URL                   | http://byk-resql       |
| OPENSEARCH_URL            | OpenSearch URL                      | http://opensearch      |
| LOG_LEVEL_ROOT            | Root log level                      | INFO                   |
| LOG_LEVEL_APP             | Application log level               | WARN                   |

### Database Setup

Run the schema migration:

```bash
psql -h your-db-host -U your-user -d your-database -f database/schema.sql
```

Or using Liquibase:

```bash
liquibase --changeLogFile=database/schema.sql update
```

### Template Setup

Insert initial templates:

```sql
INSERT INTO email_templates (id, locale, subject, html_body, text_body) VALUES
('welcome-email', 'et', 'Tere tulemast!', '<h1>Tere {{name}}</h1><p>Teretulemast!</p>', 'Tere {{name}}!');
```

## Upgrading

```bash
helm upgrade email-notification ./helm-chart \
  --namespace buerokratt \
  --values helm-chart/values.yaml \
  --set image.tag=1.0.1 \
  --reuse-values
```

## Scaling

### Manual Scaling

```bash
kubectl scale deployment email-notification-service \
  --replicas=5 -n buerokratt
```

### Autoscaling

Edit `helm-chart/values.yaml`:

```yaml
autoscaling:
  enabled: true
  minReplicas: 3
  maxReplicas: 10
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 80
```

## Monitoring

### Prometheus Integration

The service exposes metrics at `/actuator/prometheus`.

ServiceMonitor is auto-created if Prometheus operator is installed.

### Access Metrics

```bash
# Port-forward to access metrics locally
kubectl port-forward -n buerokratt svc/email-notification-service 8085:8085

# Access metrics
curl http://localhost:8085/actuator/prometheus
```

### Setup Alerts

See `docs/MONITORING.md` for alert rules configuration.

## Troubleshooting

### RabbitMQ Connection Issues

```bash
# Check RabbitMQ
kubectl exec -it rabbitmq-0 -n buerokratt -- rabbitmq-diagnostics check_running

# Test connection from pod
kubectl run test-pod --image=curlimages/curl -i --rm --restart=Never -- \
  curl telnet://rabbitmq.buerokratt.svc.cluster.local:5672
```

### Database Connection Issues

```bash
# Test database connection
kubectl run test-pod --image=postgres:14 -i --rm --restart=Never -- \
  psql postgres://user:pass@db-host:5432/database
```

### High Queue Backlog

```bash
# Check queue size
curl http://rabbitmq.buerokratt.svc.cluster.local:15672/api/queues/email.notifications/messages \
  -u guest:guest

# Scale workers
kubectl scale deployment email-notification-service --replicas=10 -n buerokratt
```

## Rollback

```bash
helm rollback email-notification -n buerokratt
```

## Uninstall

```bash
helm uninstall email-notification -n buerokratt
```

## Performance Tuning

### JVM Options

Edit deployment to tune JVM:

```yaml
env:
  - name: JAVA_OPTS
    value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Xms512m -Xmx1024m"
```

### Worker Threads

Adjust worker concurrency:

```yaml
env:
  - name: EMAIL_WORKER_THREADS
    value: "20"
```

### Connection Pools

```yaml
env:
  - name: SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE
    value: "20"
  - name: SPRING_RABBITMQ_LISTENER_CONCURRENCY
    value: "5-20"
```

## Security Hardening

### Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: email-notification-policy
  namespace: buerokratt
spec:
  podSelector:
    matchLabels:
      app: email-notification-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: ruuter
    ports:
    - protocol: TCP
      port: 8085
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: rabbitmq
    ports:
    - protocol: TCP
      port: 5672
```

### Pod Security

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 1000
  capabilities:
    drop:
    - ALL
```

## Backup and Restore

### Database Backup

```bash
kubectl exec -n buerokratt postgres-0 -- pg_dump -U user database > backup.sql
```

### Configuration Backup

```bash
kubectl get configmap -n buerokratt email-notification-dsl -o yaml > backup-dsl.yaml
```

### Restore

```bash
# Restore database
kubectl exec -i -n buerokratt postgres-0 -- psql -U user database < backup.sql

# Restore configuration
kubectl apply -f backup-dsl.yaml
```

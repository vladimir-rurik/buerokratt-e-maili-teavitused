# E-posti Teavitussüsteemi API Referents

## Base URL

```
Production: e.g. https://email.buerokratt.ee
Development: http://localhost:8085
```

## Authentication

Kõik API otspunktid nõuavad JWT autentimist TIMi kaudu.

```
Authorization: Bearer <JWT_TOKEN>
```

## Endpoints

### POST /email/send

Saada üksik e-posti teavitus.

**Päring:**

```http
POST /email/send HTTP/1.1
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>

{
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
  },
  "metadata": {
    "source": "chatbot",
    "userId": "12345"
  }
}
```

**Päringuväljad:**

| Väli           | Tüüp    | Kohustuslik | Kirjeldus                              |
|-----------------|---------|-------------|------------------------------------------|
| eventId         | string  | Ei          | Unique event identifier (auto-generated if omitted) |
| eventType       | string  | Jah         | Type of email event                      |
| recipientEmail  | string  | Jah         | Recipient email address                  |
| recipientName   | string  | Ei          | Recipient name for personalization       |
| templateId      | string  | Ei          | Template identifier                      |
| priority        | string  | Ei          | Priority: low, normal, high, critical (default: normal) |
| locale          | string  | Ei          | Locale: et, en, ru (default: et)        |
| templateData    | object  | Ei          | Data for template rendering              |
| metadata        | object  | Ei          | Additional metadata for tracking         |

**Vastus (200 OK):**

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "queued",
  "queuedAt": "2024-01-15T10:30:00.000Z"
}
```

**Vastus (400 Bad Request):**

```json
{
  "error": "Invalid email format"
}
```

### POST /email/send-batch

Saada mitu e-posti teavitist partis.

**Päring:**

```http
POST /email/send-batch HTTP/1.1
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN>

[
  {
    "eventType": "user_registration",
    "recipientEmail": "user1@example.com",
    "templateId": "welcome-email"
  },
  {
    "eventType": "user_registration",
    "recipientEmail": "user2@example.com",
    "templateId": "welcome-email"
  }
]
```

**Vastus (200 OK):**

```json
{
  "total": 2,
  "success": 2,
  "failed": 0,
  "results": [
    {
      "messageId": "id-1",
      "status": "queued"
    },
    {
      "messageId": "id-2",
      "status": "queued"
    }
  ]
}
```

### GET /email/status/{messageId}

Hangi e-posti kohaletoimetamise olek.

**Päring:**

```http
GET /email/status/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer <JWT_TOKEN>
```

**Vastus (200 OK):**

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "delivered",
  "provider": "smtp",
  "providerMessageId": "smtp-id-123",
  "attempts": 1,
  "createdAt": "2024-01-15T10:30:00.000Z",
  "sentAt": "2024-01-15T10:30:02.000Z",
  "deliveredAt": "2024-01-15T10:30:05.000Z"
}
```

**Vastus (404 Not Found):**

```json
{
  "error": "Email not found"
}
```

### POST /email/retry/{messageId}

Korda nurjunud e-kirja.

**Päring:**

```http
POST /email/retry/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer <JWT_TOKEN>
```

**Vastus (200 OK):**

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "retrying"
}
```

### DELETE /email/{messageId}

Tühista plaanitatud e-kiri.

**Päring:**

```http
DELETE /email/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer <JWT_TOKEN>
```

**Vastus (200 OK):**

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "cancelled"
}
```

**Vastus (404 Not Found):**

```json
{
  "error": "Email not found"
}
```

### GET /email/health

Tervisekontrolli otspunkt.

**Päring:**

```http
GET /email/health HTTP/1.1
```

**Vastus (200 OK):**

```json
{
  "status": "UP",
  "service": "email-notification-service"
}
```

## Event Types

| Event Type          | Template                    | Priority | Description                      |
|---------------------|-----------------------------|----------|----------------------------------|
| user_registration   | welcome-email               | normal   | New user registration            |
| password_reset      | password-reset              | high     | Password reset request           |
| chat_transferred    | chat-transfer-notification  | normal   | Chat transferred to human agent  |
| system_alert        | system-alert                | critical | System alert notifications       |
| daily_digest        | daily-summary               | low      | Daily digest emails              |

## Status Codes

| Status  | Description                     |
|---------|---------------------------------|
| queued  | Email is queued for sending     |
| processing | Email is being processed     |
| sent    | Email has been sent to provider |
| delivered | Email has been delivered     |
| failed  | Email sending failed            |
| retried | Email is being retried          |
| dlq     | Email moved to dead letter queue|

## Error Responses

Kõik vead järgivad seda vormingut:

```json
{
  "error": "Error message description"
}
```

### HTTP Status Codes

| Code | Description                           |
|------|---------------------------------------|
| 200  | Success                               |
| 400  | Bad Request (validation error)        |
| 401  | Unauthorized (invalid/missing token)  |
| 403  | Forbidden (insufficient permissions)  |
| 404  | Not Found                             |
| 429  | Too Many Requests (rate limit exceeded)|
| 500  | Internal Server Error                 |

## Rate Limiting

Vaikimisi piirangud API võtme kohta:
- 100 requests per minute
- 1000 requests per hour

Rate limit headers on kaasatud vastustesse:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642234567
```

## Examples

### cURL Examples

```bash
# Send welcome email
curl -X POST http://localhost:8085/email/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -d '{
    "eventType": "user_registration",
    "recipientEmail": "user@example.com",
    "recipientName": "John Doe",
    "templateId": "welcome-email",
    "locale": "et",
    "templateData": {
      "name": "John Doe",
      "confirmationUrl": "https://example.com/confirm"
    }
  }'

# Check email status
curl -X GET http://localhost:8085/email/status/message-id-123 \
  -H "Authorization: Bearer $JWT_TOKEN"

# Retry failed email
curl -X POST http://localhost:8085/email/retry/message-id-123 \
  -H "Authorization: Bearer $JWT_TOKEN"
```

### Java Examples

```java
// Send email
RestTemplate restTemplate = new RestTemplate();
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer " + jwtToken);
headers.setContentType(MediaType.APPLICATION_JSON);

Map<String, Object> emailRequest = Map.of(
    "eventType", "user_registration",
    "recipientEmail", "user@example.com",
    "templateId", "welcome-email",
    "locale", "et"
);

HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailRequest, headers);
ResponseEntity<Map> response = restTemplate.postForEntity(
    "http://localhost:8085/email/send",
    request,
    Map.class
);
```

### Python Examples

```python
import requests

headers = {
    "Authorization": f"Bearer {jwt_token}",
    "Content-Type": "application/json"
}

email_request = {
    "eventType": "user_registration",
    "recipientEmail": "user@example.com",
    "templateId": "welcome-email",
    "locale": "et"
}

response = requests.post(
    "http://localhost:8085/email/send",
    json=email_request,
    headers=headers
)

print(response.json())
```

## Webhooks (Future)

Webhook notifications for delivery status updates:

```http
POST YOUR_WEBHOOK_URL HTTP/1.1
Content-Type: application/json

{
  "eventId": "message-id-123",
  "status": "delivered",
  "timestamp": "2024-01-15T10:30:00.000Z"
}
```

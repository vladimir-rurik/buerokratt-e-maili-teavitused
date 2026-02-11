# Email Notification System API Reference

## Base URL

```
Production: e.g. https://email.buerokratt.ee
Development: http://localhost:8085
```

## Authentication

All API endpoints require JWT authentication via TIM.

```
Authorization: Bearer <JWT_TOKEN>
```

## Endpoints

### POST /email/send

Send a single email notification.

**Request:**

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

**Request Fields:**

| Field           | Type    | Required | Description                              |
|-----------------|---------|----------|------------------------------------------|
| eventId         | string  | No       | Unique event identifier (auto-generated if omitted) |
| eventType       | string  | Yes      | Type of email event                      |
| recipientEmail  | string  | Yes      | Recipient email address                  |
| recipientName   | string  | No       | Recipient name for personalization       |
| templateId      | string  | No       | Template identifier                      |
| priority        | string  | No       | Priority: low, normal, high, critical (default: normal) |
| locale          | string  | No       | Locale: et, en, ru (default: et)        |
| templateData    | object  | No       | Data for template rendering              |
| metadata        | object  | No       | Additional metadata for tracking         |

**Response (200 OK):**

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "queued",
  "queuedAt": "2024-01-15T10:30:00.000Z"
}
```

**Response (400 Bad Request):**

```json
{
  "error": "Invalid email format"
}
```

### POST /email/send-batch

Send multiple email notifications in batch.

**Request:**

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

**Response (200 OK):**

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

Get delivery status for an email.

**Request:**

```http
GET /email/status/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer <JWT_TOKEN>
```

**Response (200 OK):**

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

**Response (404 Not Found):**

```json
{
  "error": "Email not found"
}
```

### POST /email/retry/{messageId}

Retry a failed email.

**Request:**

```http
POST /email/retry/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer <JWT_TOKEN>
```

**Response (200 OK):**

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "retrying"
}
```

### DELETE /email/{messageId}

Cancel a scheduled email.

**Request:**

```http
DELETE /email/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer <JWT_TOKEN>
```

**Response (200 OK):**

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "cancelled"
}
```

**Response (404 Not Found):**

```json
{
  "error": "Email not found"
}
```

### GET /email/health

Health check endpoint.

**Request:**

```http
GET /email/health HTTP/1.1
```

**Response (200 OK):**

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

All errors follow this format:

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

Default limits per API key:
- 100 requests per minute
- 1000 requests per hour

Rate limit headers are included in responses:

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

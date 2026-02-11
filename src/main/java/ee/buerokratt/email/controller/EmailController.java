package ee.buerokratt.email.controller;

import ee.buerokratt.email.model.DeliveryStatus;
import ee.buerokratt.email.model.EmailRequest;
import ee.buerokratt.email.service.EmailService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for email notifications.
 *
 * Provides endpoints for sending emails, checking delivery status,
 * and managing email batches.
 */
@RestController
@RequestMapping("/email")
public class EmailController {

    private static final Logger log = LoggerFactory.getLogger(EmailController.class);
    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Send a single email notification.
     *
     * @param request Email request with recipient, template, and data
     * @return Message ID and status
     */
    @PostMapping("/send")
    @PreAuthorize("hasAuthority('SCOPE_email:send')")
    public ResponseEntity<Map<String, Object>> sendEmail(
        @Valid @RequestBody EmailRequest request
    ) {
        log.info("Received email send request for event type: {}", request.getEventType());

        try {
            Map<String, Object> result = emailService.sendEmail(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid email request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error sending email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to send email"));
        }
    }

    /**
     * Send multiple email notifications in batch.
     *
     * @param requests List of email requests
     * @return Count and message IDs
     */
    @PostMapping("/send-batch")
    @PreAuthorize("hasAuthority('SCOPE_email:send_batch')")
    public ResponseEntity<Map<String, Object>> sendBatch(
        @Valid @RequestBody List<EmailRequest> requests
    ) {
        log.info("Received batch send request for {} emails", requests.size());

        try {
            Map<String, Object> result = emailService.sendBatch(requests);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error sending batch emails", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to send batch emails"));
        }
    }

    /**
     * Get delivery status for an email.
     *
     * @param messageId Event/message ID
     * @return Delivery status information
     */
    @GetMapping("/status/{messageId}")
    @PreAuthorize("hasAuthority('SCOPE_email:read')")
    public ResponseEntity<DeliveryStatus> getStatus(
        @PathVariable String messageId
    ) {
        log.debug("Checking status for message: {}", messageId);

        DeliveryStatus status = emailService.getDeliveryStatus(messageId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Retry a failed email.
     *
     * @param messageId Event/message ID
     * @return Status of retry operation
     */
    @PostMapping("/retry/{messageId}")
    @PreAuthorize("hasAuthority('SCOPE_email:retry')")
    public ResponseEntity<Map<String, String>> retryEmail(
        @PathVariable String messageId
    ) {
        log.info("Retrying email: {}", messageId);

        try {
            emailService.retryEmail(messageId);
            return ResponseEntity.ok(Map.of(
                "messageId", messageId,
                "status", "retrying"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrying email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to retry email"));
        }
    }

    /**
     * Cancel a scheduled email.
     *
     * @param messageId Event/message ID
     * @return Status of cancellation
     */
    @DeleteMapping("/{messageId}")
    @PreAuthorize("hasAuthority('SCOPE_email:cancel')")
    public ResponseEntity<Map<String, String>> cancelEmail(
        @PathVariable String messageId
    ) {
        log.info("Cancelling email: {}", messageId);

        try {
            boolean cancelled = emailService.cancelEmail(messageId);
            if (cancelled) {
                return ResponseEntity.ok(Map.of(
                    "messageId", messageId,
                    "status", "cancelled"
                ));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error cancelling email", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to cancel email"));
        }
    }

    /**
     * Health check endpoint.
     *
     * @return Service health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "email-notification-service"
        ));
    }
}

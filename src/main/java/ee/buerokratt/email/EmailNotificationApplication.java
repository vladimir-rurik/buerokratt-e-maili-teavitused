package ee.buerokratt.email;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Email Notification Service Application
 *
 * This service handles email notifications for the Bürokraat ecosystem.
 * It integrates with Ruuter DSL workflows, RabbitMQ message queues,
 * and supports multiple email providers (SMTP, AWS SES, SendGrid).
 *
 * @author Bürokraatt Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class EmailNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailNotificationApplication.class, args);
    }
}

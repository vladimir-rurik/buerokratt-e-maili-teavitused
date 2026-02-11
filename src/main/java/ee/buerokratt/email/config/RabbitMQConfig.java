package ee.buerokratt.email.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for email notification queues.
 *
 * Configures primary email queue, retry queue, and dead letter queue.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.host:localhost}")
    private String rabbitHost;

    @Value("${rabbitmq.port:5672}")
    private int rabbitPort;

    @Value("${rabbitmq.username:guest}")
    private String rabbitUsername;

    @Value("${rabbitmq.password:guest}")
    private String rabbitPassword;

    @Value("${rabbitmq.email.queue.ttl:300000}")
    private long emailQueueTtl;

    @Value("${rabbitmq.email.dlq.ttl:86400000}")
    private long dlqTtl;

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory(rabbitHost);
        factory.setPort(rabbitPort);
        factory.setUsername(rabbitUsername);
        factory.setPassword(rabbitPassword);
        factory.setChannelCacheSize(25);
        return factory;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                // Message confirmed by broker
            } else {
                // Message not confirmed - log or handle
            }
        });
        template.setReturnsCallback(returned -> {
            // Message returned - undeliverable
        });
        return template;
    }

    // Primary Email Queue
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable("email.notifications")
            .ttl((int) emailQueueTtl)
            .maxPriority(10)
            .withArgument("x-dead-letter-exchange", "email.dlx")
            .withArgument("x-dead-letter-routing-key", "email.dlq")
            .build();
    }

    // Dead Letter Queue
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("email.dlq")
            .ttl((int) dlqTtl)
            .build();
    }

    // Retry Queue
    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable("email.retry")
            .ttl(60000) // 1 minute
            .withArgument("x-dead-letter-exchange", "email.exchange")
            .withArgument("x-dead-letter-routing-key", "email.notifications")
            .build();
    }

    // Exchanges
    @Bean
    public DirectExchange emailExchange() {
        return new DirectExchange("email.exchange", true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("email.dlx", true, false);
    }

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange("email.retry.exchange", true, false);
    }

    // Bindings
    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue())
            .to(emailExchange())
            .with("email.notifications");
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
            .to(deadLetterExchange())
            .with("email.dlq");
    }

    @Bean
    public Binding retryBinding() {
        return BindingBuilder.bind(retryQueue())
            .to(retryExchange())
            .with("email.retry");
    }
}

package kr.co.yournews.notify.config;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {
    private final RabbitMqProperties rabbitMqProperties;

    @Bean
    DirectExchange mainExchange() {
        return new DirectExchange(rabbitMqProperties.getExchangeName());
    }

    @Bean
    DirectExchange retryExchange() {
        return new DirectExchange(rabbitMqProperties.getRetryExchangeName());
    }

    @Bean
    DirectExchange deadExchange() {
        return new DirectExchange(rabbitMqProperties.getDeadExchangeName());
    }

    /**
     * Main Queue (일반 메시지 처리용) 생성
     * 소비 실패 시, Retry 큐로 이동
     */
    @Bean
    public Queue mainQueue() {
        return QueueBuilder.durable(rabbitMqProperties.getQueueName())
                .withArgument("x-dead-letter-exchange", rabbitMqProperties.getRetryExchangeName())
                .withArgument("x-dead-letter-routing-key", rabbitMqProperties.getRoutingKey() + ".retry")
                .build();
    }

    /**
     * Main Queue - Exchange 바인딩
     */
    @Bean
    public Binding mainBinding(Queue mainQueue, DirectExchange mainExchange) {
        return BindingBuilder.bind(mainQueue)
                .to(mainExchange)
                .with(rabbitMqProperties.getRoutingKey());
    }

    /**
     * Retry Queue 생성
     * TTL만큼 대기 후, 재처리를 위해 메인 큐로 이동
     */
    @Bean
    public Queue retryQueue() {
        return QueueBuilder.durable(rabbitMqProperties.getQueueName() + ".retry")
                .withArgument("x-message-ttl", rabbitMqProperties.getRetryTtl())
                .withArgument("x-dead-letter-exchange", rabbitMqProperties.getExchangeName())
                .withArgument("x-dead-letter-routing-key", rabbitMqProperties.getRoutingKey())
                .build();
    }

    /**
     * Retry Queue - Exchange 바인딩
     */
    @Bean
    public Binding retryBinding(Queue retryQueue, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue)
                .to(retryExchange)
                .with(rabbitMqProperties.getRoutingKey() + ".retry");
    }

    /**
     * Dead Letter Queue 생성 (처리 실패 메시지 저장용)
     */
    @Bean
    public Queue deadQueue() {
        return new Queue(rabbitMqProperties.getQueueName() + ".dlq", true);
    }

    /**
     * DeadLetterQueue - Exchange 바인딩
     */
    @Bean
    public Binding deadBinding(Queue deadQueue, DirectExchange deadExchange) {
        return BindingBuilder.bind(deadQueue)
                .to(deadExchange)
                .with(rabbitMqProperties.getRoutingKey() + ".dlq");
    }

    /**
     * 메시지 리스너 컨테이너 설정
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory
    ) {
        int processors = Runtime.getRuntime().availableProcessors();

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jackson2JsonMessageConverter());
        factory.setConcurrentConsumers(processors * 2);
        factory.setMaxConcurrentConsumers(processors * 3);
        factory.setPrefetchCount(10);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);

        return factory;
    }

    /**
     * 메시지 직렬화: Java <-> JSON
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}


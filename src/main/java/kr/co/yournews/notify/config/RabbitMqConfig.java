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
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitMqConfig {
    private final RabbitMqProperties rabbitMqProperties;

    /**
     * 일반 메시지 처리용 Queue 생성
     */
    @Bean
    public Queue queue() {
        return QueueBuilder.durable(rabbitMqProperties.getQueueName())
                .withArgument("x-dead-letter-exchange", rabbitMqProperties.getExchangeName())
                .withArgument("x-dead-letter-routing-key", rabbitMqProperties.getRoutingKey() + ".dlq")
                .build();
    }

    /**
     * Dead Letter Queue 생성 (처리 실패 메시지 저장용)
     */
    @Bean
    public Queue deadLetterQueue() {
        return new Queue(rabbitMqProperties.getQueueName() + ".dlq", true);
    }

    /**
     * DirectExchange 생성
     */
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(rabbitMqProperties.getExchangeName());
    }

    /**
     * Queue - Exchange 바인딩
     */
    @Bean
    public Binding binding(Queue queue, DirectExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(rabbitMqProperties.getRoutingKey());
    }

    /**
     * DeadLetterQueue - Exchange 바인딩
     */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange exchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(exchange)
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
        factory.setAdviceChain(retryInterceptor());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        return factory;
    }

    /**
     * 재시도 정책 설정
     */
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(500, 2.0, 3000)  // 초기 0.5초, 배수 2.0, 최대 3초
                .recoverer((message, cause) -> {
                    log.error("Message failed after retries: {}. Cause: {}", message, cause.getMessage(), cause);
                    new RejectAndDontRequeueRecoverer().recover(message, cause);
                })
                .build();
    }

    /**
     * 메시지 직렬화: Java <-> JSON
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}


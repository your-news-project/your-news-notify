package kr.co.yournews.notify.config.rabbitmq;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@RequiredArgsConstructor
public class RabbitMqMainTopologyConfig {
    private final RabbitMqProperties rabbitMqProperties;

    /**
     * 메인 처리 큐
     */
    @Bean
    public Queue mainQueue() {
        return QueueBuilder.durable(rabbitMqProperties.getMainQueueName())
                .build();
    }

    /**
     * 1차 재시도 큐
     */
    @Bean
    public Queue firstRetryQueue() {
        return buildRetryQueue(
                rabbitMqProperties.getRetryQueueName() + ".1",
                rabbitMqProperties.getFirstRetryTtl()
        );
    }

    /**
     * 2차 재시도 큐
     */
    @Bean
    public Queue secondRetryQueue() {
        return buildRetryQueue(
                rabbitMqProperties.getRetryQueueName() + ".2",
                rabbitMqProperties.getSecondRetryTtl()
        );
    }

    /**
     * 3차 재시도 큐
     */
    @Bean
    public Queue thirdRetryQueue() {
        return buildRetryQueue(
                rabbitMqProperties.getRetryQueueName() + ".3",
                rabbitMqProperties.getThirdRetryTtl()
        );
    }

    /**
     * 메인 큐 바인딩
     */
    @Bean
    public Binding mainQueueBinding(Queue mainQueue, DirectExchange mainExchange) {
        return buildBinding(
                mainQueue,
                mainExchange,
                rabbitMqProperties.getMainRoutingKey()
        );
    }

    /**
     * 1차 재시도 큐 바인딩
     */
    @Bean
    public Binding firstRetryQueueBinding(Queue firstRetryQueue, DirectExchange retryExchange) {
        return buildBinding(
                firstRetryQueue,
                retryExchange,
                rabbitMqProperties.getRetryRoutingKey() + ".1"
        );
    }

    /**
     * 2차 재시도 큐 바인딩
     */
    @Bean
    public Binding secondRetryQueueBinding(Queue secondRetryQueue, DirectExchange retryExchange) {
        return buildBinding(
                secondRetryQueue,
                retryExchange,
                rabbitMqProperties.getRetryRoutingKey() + ".2"
        );
    }

    /**
     * 3차 재시도 큐 바인딩
     */
    @Bean
    public Binding thirdRetryQueueBinding(Queue thirdRetryQueue, DirectExchange retryExchange) {
        return buildBinding(
                thirdRetryQueue,
                retryExchange,
                rabbitMqProperties.getRetryRoutingKey() + ".3"
        );
    }

    private Queue buildRetryQueue(String queueName, int ttl) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", ttl)
                .withArgument("x-dead-letter-exchange", rabbitMqProperties.getMainExchangeName())
                .withArgument("x-dead-letter-routing-key", rabbitMqProperties.getMainRoutingKey())
                .build();
    }

    private Binding buildBinding(Queue queue, DirectExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(routingKey);
    }
}

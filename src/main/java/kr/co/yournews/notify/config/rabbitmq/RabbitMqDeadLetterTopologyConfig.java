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
public class RabbitMqDeadLetterTopologyConfig {
    private final RabbitMqProperties rabbitMqProperties;

    /**
     * DLQ 진입 큐
     * 일정 시간 대기 후 DLQ 처리 큐로 이동
     */
    @Bean
    public Queue deadLetterQueue() {
        return buildDelayQueue(
                rabbitMqProperties.getDlqQueueName(),
                rabbitMqProperties.getDlqDelayTtl()
        );
    }

    /**
     * DLQ 실제 처리 큐
     */
    @Bean
    public Queue deadLetterProcessQueue() {
        return QueueBuilder.durable(rabbitMqProperties.getDlqQueueName() + ".process")
                .build();
    }

    /**
     * DLQ 1차 재시도 큐
     */
    @Bean
    public Queue firstDeadLetterRetryQueue() {
        return buildDelayQueue(
                rabbitMqProperties.getDlqQueueName() + ".retry.1",
                rabbitMqProperties.getFirstDlqRetryTtl()
        );
    }

    /**
     * DLQ 2차 재시도 큐
     */
    @Bean
    public Queue secondDeadLetterRetryQueue() {
        return buildDelayQueue(
                rabbitMqProperties.getDlqQueueName() + ".retry.2",
                rabbitMqProperties.getSecondDlqRetryTtl()
        );
    }

    /**
     * DLQ 3차 재시도 큐
     */
    @Bean
    public Queue thirdDeadLetterRetryQueue() {
        return buildDelayQueue(
                rabbitMqProperties.getDlqQueueName() + ".retry.3",
                rabbitMqProperties.getThirdDlqRetryTtl()
        );
    }

    /**
     * 최종 격리 큐
     */
    @Bean
    public Queue parkingQueue() {
        return QueueBuilder.durable(rabbitMqProperties.getParkingQueueName())
                .build();
    }

    /**
     * DLQ 진입 큐 바인딩
     */
    @Bean
    public Binding deadLetterQueueBinding(Queue deadLetterQueue, DirectExchange deadExchange) {
        return buildBinding(
                deadLetterQueue,
                deadExchange,
                rabbitMqProperties.getDlqRoutingKey()
        );
    }

    /**
     * DLQ 처리 큐 바인딩
     */
    @Bean
    public Binding deadLetterProcessQueueBinding(
            Queue deadLetterProcessQueue,
            DirectExchange deadExchange
    ) {
        return buildBinding(
                deadLetterProcessQueue,
                deadExchange,
                rabbitMqProperties.getDlqRoutingKey() + ".process"
        );
    }

    /**
     * DLQ 1차 재시도 큐 바인딩
     */
    @Bean
    public Binding firstDeadLetterRetryQueueBinding(
            Queue firstDeadLetterRetryQueue,
            DirectExchange deadExchange
    ) {
        return buildBinding(
                firstDeadLetterRetryQueue,
                deadExchange,
                rabbitMqProperties.getDlqRoutingKey() + ".retry.1"
        );
    }

    /**
     * DLQ 2차 재시도 큐 바인딩
     */
    @Bean
    public Binding secondDeadLetterRetryQueueBinding(
            Queue secondDeadLetterRetryQueue,
            DirectExchange deadExchange
    ) {
        return buildBinding(
                secondDeadLetterRetryQueue,
                deadExchange,
                rabbitMqProperties.getDlqRoutingKey() + ".retry.2"
        );
    }

    /**
     * DLQ 3차 재시도 큐 바인딩
     */
    @Bean
    public Binding thirdDeadLetterRetryQueueBinding(
            Queue thirdDeadLetterRetryQueue,
            DirectExchange deadExchange
    ) {
        return buildBinding(
                thirdDeadLetterRetryQueue,
                deadExchange,
                rabbitMqProperties.getDlqRoutingKey() + ".retry.3"
        );
    }

    /**
     * Parking 큐 바인딩
     */
    @Bean
    public Binding parkingQueueBinding(Queue parkingQueue, DirectExchange deadExchange) {
        return buildBinding(
                parkingQueue,
                deadExchange,
                rabbitMqProperties.getParkingRoutingKey()
        );
    }

    private Queue buildDelayQueue(String queueName, int ttl) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-message-ttl", ttl)
                .withArgument("x-dead-letter-exchange", rabbitMqProperties.getDeadExchangeName())
                .withArgument("x-dead-letter-routing-key", rabbitMqProperties.getDlqRoutingKey() + ".process")
                .build();
    }

    private Binding buildBinding(Queue queue, DirectExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(routingKey);
    }
}

package kr.co.yournews.notify.config.rabbitmq;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMqExchangeConfig {
    private final RabbitMqProperties rabbitMqProperties;

    @Bean
    public DirectExchange mainExchange() {
        return new DirectExchange(rabbitMqProperties.getMainExchangeName());
    }

    @Bean
    public DirectExchange retryExchange() {
        return new DirectExchange(rabbitMqProperties.getRetryExchangeName());
    }

    @Bean
    public DirectExchange deadExchange() {
        return new DirectExchange(rabbitMqProperties.getDeadExchangeName());
    }
}

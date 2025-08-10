package kr.co.yournews.notify.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rabbitmq")
@Getter @Setter
public class RabbitMqProperties {
    private String queueName;
    private String exchangeName;
    private String routingKey;
}

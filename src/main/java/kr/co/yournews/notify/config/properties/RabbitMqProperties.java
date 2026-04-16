package kr.co.yournews.notify.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rabbitmq")
@Getter @Setter
public class RabbitMqProperties {
    private String mainExchangeName;
    private String retryExchangeName;
    private String deadExchangeName;

    private String mainQueueName;
    private String retryQueueName;
    private String dlqQueueName;
    private String parkingQueueName;

    private String mainRoutingKey;
    private String retryRoutingKey;
    private String dlqRoutingKey;
    private String parkingRoutingKey;

    private int firstRetryTtl;
    private int secondRetryTtl;
    private int thirdRetryTtl;

    private int dlqDelayTtl;
    private int firstDlqRetryTtl;
    private int secondDlqRetryTtl;
    private int thirdDlqRetryTtl;
}

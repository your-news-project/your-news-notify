package kr.co.yournews.notify.consumer;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import kr.co.yournews.notify.consumer.dto.FcmMessageDto;
import kr.co.yournews.notify.fcm.sender.FcmNotificationSender;
import kr.co.yournews.notify.fcm.sender.exception.FcmSendFailureException;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import kr.co.yournews.notify.fcm.token.service.FcmTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmNotificationConsumerTest {

    @Mock
    private FcmNotificationSender fcmNotificationSender;

    @Mock
    private FcmTokenService fcmTokenService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitMqProperties rabbitMqProperties;

    @InjectMocks
    private FcmNotificationConsumer fcmNotificationConsumer;

    private static final String QUEUE = "queue";
    private static final String DEAD_EXCHANGE = "dead.exchange";
    private static final String ROUTING_KEY = "key";

    private static final FcmMessageDto dto =
            new FcmMessageDto(
                    "token",
                    "title",
                    "publicId",
                    true,
                    false
            );

    private Message amqpWithXDeath(long count) {
        MessageProperties mp = new MessageProperties();
        Map<String, Object> death = new HashMap<>();
        death.put("queue", QUEUE);
        death.put("count", count);
        mp.setHeader("x-death", List.of(death));
        return new Message(new byte[0], mp);
    }

    @Test
    @DisplayName("컷오프 도달 ⇒ DLQ로 수동 전송")
    void cutoffThenSendToDlq() {
        // given
        when(rabbitMqProperties.getQueueName()).thenReturn(QUEUE);
        when(rabbitMqProperties.getDeadExchangeName()).thenReturn(DEAD_EXCHANGE);
        when(rabbitMqProperties.getRoutingKey()).thenReturn(ROUTING_KEY);
        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.failure("any-error"));

        // deathCount = 2 → nextAttempt = 3 → MAX_RETRY = 3 도달
        Message amqp = amqpWithXDeath(2);

        // when
        fcmNotificationConsumer.handleMessage(dto, amqp);

        // then
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(DEAD_EXCHANGE), eq(ROUTING_KEY + ".dlq"), eq(dto));
        verify(fcmTokenService, never()).removeByToken(anyString());
    }

    @Test
    @DisplayName("컷오프 미도달 ⇒ RuntimeException 던져 재시도 큐로 이동")
    void notCutoffThenThrowForRetry() {
        // given
        when(rabbitMqProperties.getQueueName()).thenReturn(QUEUE);

        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.failure("retryable"));

        // deathCount = 1 → nextAttempt = 2 → MAX_RETRY = 3 미도달
        Message amqp = amqpWithXDeath(1);

        // when & then
        assertThrows(FcmSendFailureException.class,
                () -> fcmNotificationConsumer.handleMessage(dto, amqp));

        verify(rabbitTemplate, never()).convertAndSend(anyString(), Optional.of(anyString()), any(), any());
    }

    @Test
    @DisplayName("비재시도 케이스 ⇒ 토큰 삭제")
    void nonRetryRemoveInvalidToken() {
        // given
        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.invalidToken("bad-token"));

        Message amqp = amqpWithXDeath(3);

        // when
        fcmNotificationConsumer.handleMessage(dto, amqp);

        // then
        verify(fcmTokenService, times(1)).removeByToken(eq("token"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), Optional.of(anyString()), any(), any());
    }
}

package kr.co.yournews.notify.consumer;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import kr.co.yournews.notify.consumer.dto.FcmMessageDto;
import kr.co.yournews.notify.fcm.sender.FcmNotificationSender;
import kr.co.yournews.notify.fcm.sender.exception.FcmSendFailureException;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import kr.co.yournews.notify.message.process.model.MessageProcessStatus;
import kr.co.yournews.notify.message.process.model.MessageProcessClaim;
import kr.co.yournews.notify.message.process.service.MessageProcessService;
import kr.co.yournews.notify.token.service.FcmTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Mock
    private MessageProcessService processService;

    @InjectMocks
    private FcmNotificationConsumer fcmNotificationConsumer;

    private static final String DEAD_EXCHANGE = "dead.exchange";
    private static final String ROUTING_KEY = "key";

    private static final FcmMessageDto dto =
            new FcmMessageDto(
                    "token",
                    "title",
                    "content",
                    "notification",
                    "public-id"
            );

    @Test
    @DisplayName("컷오프 도달 ⇒ DLQ로 수동 전송")
    void cutoffThenSendToDlq() {
        // given
        when(processService.claim(anyString(), anyString(), eq(3)))
                .thenReturn(MessageProcessClaim.claimed(3));
        when(rabbitMqProperties.getDeadExchangeName()).thenReturn(DEAD_EXCHANGE);
        when(rabbitMqProperties.getRoutingKey()).thenReturn(ROUTING_KEY);
        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.failure("any-error"));

        // when
        fcmNotificationConsumer.handleMessage(dto);

        // then
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(DEAD_EXCHANGE), eq(ROUTING_KEY + ".dlq"), eq(dto));
        verify(processService, times(1)).markFailedRetryExhausted(anyString(), eq("any-error"));
        verify(fcmTokenService, never()).removeByToken(anyString());
    }

    @Test
    @DisplayName("컷오프 미도달 ⇒ RuntimeException 던져 재시도 큐로 이동")
    void notCutoffThenThrowForRetry() {
        // given
        when(processService.claim(anyString(), anyString(), eq(3)))
                .thenReturn(MessageProcessClaim.claimed(1));
        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.failure("retryable"));

        // when & then
        assertThrows(FcmSendFailureException.class,
                () -> fcmNotificationConsumer.handleMessage(dto));

        verify(processService, times(1)).markRetryPending(anyString(), eq("retryable"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), eq(dto));
    }

    @Test
    @DisplayName("비재시도 케이스 ⇒ 토큰 삭제 + 영구 실패 마킹")
    void nonRetryRemoveInvalidToken() {
        // given
        when(processService.claim(anyString(), anyString(), eq(3)))
                .thenReturn(MessageProcessClaim.claimed(1));
        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.invalidToken("bad-token"));

        // when
        fcmNotificationConsumer.handleMessage(dto);

        // then
        verify(fcmTokenService, times(1)).removeByToken(eq("token"));
        verify(processService, times(1)).markFailedPermanent(anyString(), eq("bad-token"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), eq(dto));
    }

    @Test
    @DisplayName("기처리 상태면 중복 처리 스킵")
    void skipWhenAlreadyProcessed() {
        // given
        when(processService.claim(anyString(), anyString(), eq(3)))
                .thenReturn(MessageProcessClaim.skipped(3, MessageProcessStatus.SUCCEEDED));

        // when
        fcmNotificationConsumer.handleMessage(dto);

        // then
        verify(fcmNotificationSender, never()).sendNotification(anyString(), anyString(), anyString(), anyMap());
        verify(processService, never()).markSuccess(anyString());
    }
}

package kr.co.yournews.notify.consumer;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import kr.co.yournews.notify.consumer.dto.FcmMessageDto;
import kr.co.yournews.notify.fcm.sender.FcmNotificationSender;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import kr.co.yournews.notify.message.process.model.MessageProcess;
import kr.co.yournews.notify.message.process.model.MessageProcessStatus;
import kr.co.yournews.notify.message.process.service.MessageProcessService;
import kr.co.yournews.notify.token.service.FcmTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmNotificationConsumerTest {

    private static final int MAX_RETRY = 4;

    private static final String DEAD_EXCHANGE = "dead.exchange";
    private static final String RETRY_EXCHANGE = "retry.exchange";
    private static final String DLQ_ROUTING_KEY = "fcm.dlq";
    private static final String RETRY_ROUTING_KEY = "fcm.retry";

    private static final FcmMessageDto DTO = new FcmMessageDto(
            "token",
            "title",
            "content",
            "notification",
            "public-id"
    );

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

    @Test
    @DisplayName("최대 재시도 횟수에 도달하면 DLQ로 이동한다")
    void sendToDlqWhenRetryExhausted() {
        // given
        when(processService.claim(anyString(), anyString(), eq(MAX_RETRY)))
                .thenReturn(MessageProcess.claimed(4));

        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.failure("any-error"));

        when(rabbitMqProperties.getDeadExchangeName()).thenReturn(DEAD_EXCHANGE);
        when(rabbitMqProperties.getDlqRoutingKey()).thenReturn(DLQ_ROUTING_KEY);

        // when
        fcmNotificationConsumer.handleMessage(DTO);

        // then
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(DEAD_EXCHANGE), eq(DLQ_ROUTING_KEY), eq(DTO));

        verify(processService, times(1))
                .markFailedRetryExhausted(anyString(), eq("any-error"));

        verify(processService, never())
                .markRetryPending(anyString(), anyString());

        verify(fcmTokenService, never()).removeByToken(anyString());
    }

    @Test
    @DisplayName("1차 재시도 대상이면 retry.1 큐로 전송한다")
    void publishToFirstRetryQueue() {
        // given
        when(processService.claim(anyString(), anyString(), eq(MAX_RETRY)))
                .thenReturn(MessageProcess.claimed(1));

        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.failure("retryable"));

        when(rabbitMqProperties.getRetryExchangeName()).thenReturn(RETRY_EXCHANGE);
        when(rabbitMqProperties.getRetryRoutingKey()).thenReturn(RETRY_ROUTING_KEY);

        // when
        fcmNotificationConsumer.handleMessage(DTO);

        // then
        verify(processService, times(1))
                .markRetryPending(anyString(), eq("retryable"));

        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(RETRY_EXCHANGE), eq(RETRY_ROUTING_KEY + ".1"), eq(DTO));

        verify(processService, never())
                .markFailedRetryExhausted(anyString(), anyString());
    }

    @Test
    @DisplayName("2차 재시도 대상이면 retry.2 큐로 전송한다")
    void publishToSecondRetryQueue() {
        // given
        when(processService.claim(anyString(), anyString(), eq(MAX_RETRY)))
                .thenReturn(MessageProcess.claimed(2));

        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.failure("retryable"));

        when(rabbitMqProperties.getRetryExchangeName()).thenReturn(RETRY_EXCHANGE);
        when(rabbitMqProperties.getRetryRoutingKey()).thenReturn(RETRY_ROUTING_KEY);

        // when
        fcmNotificationConsumer.handleMessage(DTO);

        // then
        verify(processService, times(1))
                .markRetryPending(anyString(), eq("retryable"));

        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(RETRY_EXCHANGE), eq(RETRY_ROUTING_KEY + ".2"), eq(DTO));

        verify(processService, never())
                .markFailedRetryExhausted(anyString(), anyString());
    }

    @Test
    @DisplayName("3차 재시도 대상이면 retry.3 큐로 전송한다")
    void publishToThirdRetryQueue() {
        // given
        when(processService.claim(anyString(), anyString(), eq(MAX_RETRY)))
                .thenReturn(MessageProcess.claimed(3));

        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.failure("retryable"));

        when(rabbitMqProperties.getRetryExchangeName()).thenReturn(RETRY_EXCHANGE);
        when(rabbitMqProperties.getRetryRoutingKey()).thenReturn(RETRY_ROUTING_KEY);

        // when
        fcmNotificationConsumer.handleMessage(DTO);

        // then
        verify(processService, times(1))
                .markRetryPending(anyString(), eq("retryable"));

        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(RETRY_EXCHANGE), eq(RETRY_ROUTING_KEY + ".3"), eq(DTO));

        verify(processService, never())
                .markFailedRetryExhausted(anyString(), anyString());
    }

    @Test
    @DisplayName("비재시도 실패면 토큰 삭제 후 영구 실패 처리한다")
    void removeInvalidTokenWhenNonRetryableFailure() {
        // given
        when(processService.claim(anyString(), anyString(), eq(MAX_RETRY)))
                .thenReturn(MessageProcess.claimed(1));

        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.invalidToken("bad-token"));

        // when
        fcmNotificationConsumer.handleMessage(DTO);

        // then
        verify(fcmTokenService, times(1)).removeByToken(eq("token"));
        verify(processService, times(1)).markFailedPermanent(anyString(), eq("bad-token"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), eq(DTO));
    }

    @Test
    @DisplayName("이미 처리된 상태면 중복 처리를 스킵한다")
    void skipWhenAlreadyProcessed() {
        // given
        when(processService.claim(anyString(), anyString(), eq(MAX_RETRY)))
                .thenReturn(MessageProcess.skipped(4, MessageProcessStatus.SUCCEEDED));

        // when
        fcmNotificationConsumer.handleMessage(DTO);

        // then
        verify(fcmNotificationSender, never())
                .sendNotification(anyString(), anyString(), anyString(), anyMap());

        verify(processService, never()).markSuccess(anyString());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), eq(DTO));
    }

    @Test
    @DisplayName("전송 성공이면 성공 상태로 마킹한다")
    void markSuccessWhenSendSucceeded() {
        // given
        when(processService.claim(anyString(), anyString(), eq(MAX_RETRY)))
                .thenReturn(MessageProcess.claimed(1));

        when(fcmNotificationSender.sendNotification(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(FcmSendResult.success("success"));

        // when
        fcmNotificationConsumer.handleMessage(DTO);

        // then
        verify(processService, times(1)).markSuccess(anyString());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), eq(DTO));
        verify(fcmTokenService, never()).removeByToken(anyString());
    }
}
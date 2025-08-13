package kr.co.yournews.notify.consumer;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import kr.co.yournews.notify.consumer.dto.FcmMessageDto;
import kr.co.yournews.notify.fcm.sender.FcmNotificationSender;
import kr.co.yournews.notify.fcm.sender.constant.FcmConstant;
import kr.co.yournews.notify.fcm.sender.exception.FcmSendFailureException;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import kr.co.yournews.notify.fcm.token.service.FcmTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmNotificationConsumer {
    private final FcmNotificationSender fcmNotificationSender;
    private final FcmTokenService fcmTokenService;
    private final RabbitMqProperties rabbitMqProperties;
    private final RabbitTemplate rabbitTemplate;

    private static final int MAX_RETRY = 3;                 // 재시도 횟수
    private static final String X_DEATH = "x-death";        // Rabbit header key
    private static final String HDR_QUEUE = "queue";        // x-death 필드
    private static final String HDR_COUNT = "count";        // x-death 필드

    /**
     * RabbitMQ로부터 수신된 FCM 메시지를 처리하는 메서드
     * <p>
     * 1. FCM 서버에 푸시 알림을 전송
     * 2. 전송 결과에 따라 유효하지 않은 토큰을 삭제
     * 3. 전송 실패 시 RuntimeException을 발생시켜 재시도 처리를 유도함
     * 4. 재시도를 실패하면, DLQ로 이동
     *
     * @param message : (FCM 토큰, 알림 제목, 알림 내용)
     */
    @RabbitListener(queues = "${rabbitmq.queue-name}", containerFactory = "rabbitListenerContainerFactory")
    public void handleMessage(@Payload FcmMessageDto message, Message amqpMessage) {
        if (message.isFirst()) {
            log.info("[FCM] 소식 단위 전송 시작 (추정) - title: {}", message.title());
        }

        String content = FcmConstant.NEWS_NOTIFICATION_CONTENT;
        Map<String, String> data = buildMessageData(message.data());

        FcmSendResult result = fcmNotificationSender.sendNotification(
                message.token(), message.title(), content, data
        );

        // 비재시도: 잘못된/만료 토큰
        if (result.shouldRemoveToken()) {
            log.warn("[FCM] 유효하지 않은 토큰 삭제 - token: {}", message.token());
            fcmTokenService.removeByToken(message.token());
            return;
        }

        // 실패 → 재시도/컷오프 판단
        if (!result.success()) {
            int deathCount = extractDeathCount(amqpMessage, rabbitMqProperties.getQueueName());
            int nextAttempt = deathCount + 1;

            log.error("[FCM] 전송 실패 - token: {}, reason: {}, x-death={}, nextAttempt={}",
                    message.token(), result.message(), deathCount, nextAttempt);

            // 최대 재시도 횟수 도달 → 최종 DLQ로 격리 후 ACK (루프 종료)
            if (nextAttempt >= MAX_RETRY) {
                rabbitTemplate.convertAndSend(
                        rabbitMqProperties.getDeadExchangeName(),
                        rabbitMqProperties.getRoutingKey() + ".dlq",
                        message
                );

                log.error("[FCM] 최종 실패 → DLQ로 이동 - token: {}", message.token());
                return;
            }

            // 컷오프 전: 예외 던져 NACK → 재시도 큐로 이동
            throw new FcmSendFailureException(result.message());
        }


        if (message.isLast()) {
            log.info("[FCM] 소식 단위 전송 완료 (추정) - title: {}", message.title());
        }
    }

    /**
     * Notification data 생성 메서드
     *
     * @param publicId : 알림 추가 데이터
     * @return data가 저장된 Map 자료구조
     */
    private Map<String, String> buildMessageData(String publicId) {
        Map<String, String> data = new HashMap<>();
        data.put("publicId", publicId);
        return data;
    }

    /**
     * x-death 헤더에서 해당 큐 기준 누적 실패 횟수 추출
     */
    @SuppressWarnings("unchecked")
    private int extractDeathCount(Message amqpMessage, String queueName) {
        MessageProperties props = amqpMessage.getMessageProperties();
        List<Map<String, Object>> deaths =
                (List<Map<String, Object>>) props.getHeaders().get(X_DEATH);
        if (deaths == null) return 0;

        return deaths.stream()
                .filter(d -> queueName.equals(d.get(HDR_QUEUE)))
                .mapToInt(d -> ((Long) d.getOrDefault(HDR_COUNT, 0L)).intValue())
                .sum();
    }
}


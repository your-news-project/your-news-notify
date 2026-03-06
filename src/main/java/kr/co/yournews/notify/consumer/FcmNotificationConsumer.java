package kr.co.yournews.notify.consumer;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import kr.co.yournews.notify.consumer.dto.FcmMessageDto;
import kr.co.yournews.notify.fcm.sender.FcmNotificationSender;
import kr.co.yournews.notify.fcm.sender.exception.FcmSendFailureException;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import kr.co.yournews.notify.message.process.util.IdempotencyKeyUtil;
import kr.co.yournews.notify.message.process.model.MessageProcess;
import kr.co.yournews.notify.message.process.service.MessageProcessService;
import kr.co.yournews.notify.token.service.FcmTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 메시지 처리 컨슈머
 */
@Slf4j
@Component
public class FcmNotificationConsumer extends AbstractFcmConsumer {
    private final RabbitMqProperties rabbitMqProperties;
    private final RabbitTemplate rabbitTemplate;

    private static final int MAX_RETRY = 3;                 // 재시도 횟수

    public FcmNotificationConsumer(
            FcmNotificationSender fcmNotificationSender,
            FcmTokenService fcmTokenService,
            RabbitMqProperties rabbitMqProperties,
            RabbitTemplate rabbitTemplate,
            MessageProcessService processService
    ) {
        super(fcmNotificationSender, fcmTokenService, processService);
        this.rabbitMqProperties = rabbitMqProperties;
        this.rabbitTemplate = rabbitTemplate;
    }

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
    @RabbitListener(
            queues = "${rabbitmq.queue-name}",
            containerFactory = "fcmListenerContainerFactory"
    )
    public void handleMessage(@Payload FcmMessageDto message) {
        String tokenHash = IdempotencyKeyUtil.hash(message.token());
        String idempKey = IdempotencyKeyUtil.getKey(message.info(), message.token());

        // 같은 idempotency key는 한 번만 처리권(claim)을 얻음 (중복 처리 방지)
        MessageProcess claim = processService.claim(idempKey, tokenHash, MAX_RETRY);
        if (!claim.claimed()) {
            log.info("[FCM] 중복/기처리 스킵 - key={}, status={}, attempt={}",
                    idempKey, claim.status(), claim.attemptCount());
            return;
        }

        FcmSendResult result = sendMessage(message);

        // 비재시도: 잘못된/만료 토큰
        if (handleInvalidToken("FCM", message, idempKey, result)) {
            return;
        }

        // 실패 → 재시도/컷오프 판단
        if (!result.success()) {
            // 최대 재시도 횟수 도달 → 최종 DLQ로 격리 후 ACK (루프 종료)
            if (claim.attemptCount() >= MAX_RETRY) {
                rabbitTemplate.convertAndSend(
                        rabbitMqProperties.getDeadExchangeName(),
                        rabbitMqProperties.getRoutingKey() + ".dlq",
                        message
                );

                // 재시도 소진 후 최종 실패 상태로 확정
                processService.markFailedRetryExhausted(idempKey, result.message());
                log.error("[FCM] 최종 실패 → DLQ로 이동 - token: {}", message.token());
                return;
            }

            log.warn("[FCM] 전송 실패 - token: {}, reason: {}, attempt={}",
                    message.token(), result.message(), claim.attemptCount());

            // 재시도 가능 실패는 상태만 남기고 예외로 DLX 재큐잉
            processService.markRetryPending(idempKey, result.message());
            // 컷오프 전: 예외 던져 NACK → 재시도 큐로 이동
            throw new FcmSendFailureException(result.message());
        }

        // 성공이면 최종 완료로 마킹
        processService.markSuccess(idempKey);
    }
}

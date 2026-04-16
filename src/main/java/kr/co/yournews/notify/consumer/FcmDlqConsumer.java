package kr.co.yournews.notify.consumer;

import kr.co.yournews.notify.config.properties.RabbitMqProperties;
import kr.co.yournews.notify.consumer.dto.FcmMessageDto;
import kr.co.yournews.notify.fcm.sender.FcmNotificationSender;
import kr.co.yournews.notify.fcm.sender.exception.FcmSendFailureException;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import kr.co.yournews.notify.message.process.model.MessageProcess;
import kr.co.yournews.notify.message.process.model.MessageProcessStatus;
import kr.co.yournews.notify.message.process.service.MessageProcessService;
import kr.co.yournews.notify.message.process.util.IdempotencyKeyUtil;
import kr.co.yournews.notify.token.service.FcmTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * DLQ 메시지 처리 컨슈머
 */
@Slf4j
@Component
public class FcmDlqConsumer extends AbstractFcmConsumer {
    private final RabbitMqProperties rabbitMqProperties;
    private final RabbitTemplate rabbitTemplate;

    private static final int MAX_DLQ_RETRY = 4;

    public FcmDlqConsumer(
            FcmNotificationSender fcmNotificationSender,
            FcmTokenService fcmTokenService,
            MessageProcessService processService,
            RabbitMqProperties rabbitMqProperties,
            RabbitTemplate rabbitTemplate
    ) {
        super(fcmNotificationSender, fcmTokenService, processService);
        this.rabbitMqProperties = rabbitMqProperties;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(
            queues = "#{rabbitMqProperties.dlqQueueName + '.process'}",
            containerFactory = "fcmListenerContainerFactory"
    )
    public void handleDlqMessage(@Payload FcmMessageDto message) {
        String idempKey = IdempotencyKeyUtil.getKey(message.info(), message.token());

        Optional<MessageProcess> target = processService.findByIdempotencyKey(idempKey);
        if (target.isEmpty()) {
            log.warn("[FCM-DLQ] 처리 상태 미존재로 스킵 - key={}", idempKey);
            return;
        }

        MessageProcess messageProcess = target.get();
        if (messageProcess.status() == MessageProcessStatus.FAILED_PERMANENT
                || messageProcess.status() == MessageProcessStatus.FAILED_FINAL
                || messageProcess.status() == MessageProcessStatus.SUCCEEDED) {
            log.info("[FCM-DLQ] 영구실패/기성공 상태로 스킵 - key={}, status={}", idempKey, messageProcess.status());
            return;
        }

        // 시도 횟수 카운트
        int dlqRetryCount = messageProcess.attemptCount() + 1;
        processService.increaseDlqAttempt(idempKey);

        FcmSendResult result = sendMessage(message);

        // 비재시도: 잘못된/만료 토큰
        if (handleInvalidToken("FCM-DLQ", message, idempKey, result)) {
            return;
        }

        // 실패 → 재시도/컷오프 판단
        if (!result.success()) {
            // 최대 재시도 횟수 도달 → 메시지 상태 업데이트 후 종료
            if (dlqRetryCount >= MAX_DLQ_RETRY) {
                rabbitTemplate.convertAndSend(
                        rabbitMqProperties.getDeadExchangeName(),
                        rabbitMqProperties.getParkingRoutingKey(),
                        message
                );

                processService.markFailedFinal(idempKey, result.message());
                log.error("[FCM-DLQ] 최종 실패 - key={}, retry={}, reason={}",
                        idempKey, dlqRetryCount, result.message());
                return;
            }

            String retryRoutingKey = resolveDlqRetryRoutingKey(dlqRetryCount);

            rabbitTemplate.convertAndSend(
                    rabbitMqProperties.getDeadExchangeName(),
                    retryRoutingKey,
                    message
            );

            log.warn("[FCM-DLQ] 재처리 실패 - retry queue로 재전달 - key={}, retry={}, routingKey={}, reason={}",
                    idempKey, dlqRetryCount, retryRoutingKey, result.message());
            return;
        }

        // 성공이면 최종 완료로 마킹
        processService.markSuccess(idempKey);
        log.info("[FCM-DLQ] 재처리 성공 - key={}", idempKey);
    }

    private String resolveDlqRetryRoutingKey(int dlqRetryCount) {
        return switch (dlqRetryCount) {
            case 1 -> rabbitMqProperties.getDlqRoutingKey() + ".retry.1";
            case 2 -> rabbitMqProperties.getDlqRoutingKey() + ".retry.2";
            case 3 -> rabbitMqProperties.getDlqRoutingKey() + ".retry.3";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 DLQ 재시도 횟수입니다. dlqRetryCount=" + dlqRetryCount
            );
        };
    }
}

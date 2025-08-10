package kr.co.yournews.notify.consumer;

import kr.co.yournews.notify.consumer.dto.FcmMessageDto;
import kr.co.yournews.notify.fcm.sender.FcmNotificationSender;
import kr.co.yournews.notify.fcm.sender.constant.FcmConstant;
import kr.co.yournews.notify.fcm.sender.exception.FcmSendFailureException;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import kr.co.yournews.notify.fcm.token.service.FcmTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmNotificationConsumer {
    private final FcmNotificationSender fcmNotificationSender;
    private final FcmTokenService fcmTokenService;

    /**
     * RabbitMQ로부터 수신된 FCM 메시지를 처리하는 메서드
     *
     * 1. FCM 서버에 푸시 알림을 전송
     * 2. 전송 결과에 따라 유효하지 않은 토큰을 삭제
     * 3. 전송 실패 시 RuntimeException을 발생시켜 재시도 처리를 유도함
     *
     * @param message : (FCM 토큰, 알림 제목, 알림 내용)
     */
    @RabbitListener(queues = "${rabbitmq.queue-name}", containerFactory = "rabbitListenerContainerFactory")
    public void handleMessage(FcmMessageDto message) {
        if (message.isFirst()) {
            log.info("[FCM] 소식 단위 전송 시작 (추정) - title: {}", message.title());
        }

        String content = FcmConstant.NEWS_NOTIFICATION_CONTENT;
        Map<String, String> data = buildMessageData(message.data());

        FcmSendResult result = fcmNotificationSender.sendNotification(
                message.token(), message.title(), content, data
        );

        if (result.shouldRemoveToken()) {
            log.warn("[FCM] 유효하지 않은 토큰 삭제 - token: {}", message.token());
            fcmTokenService.removeByToken(message.token());
            return;
        }

        if (!result.success()) {
            log.error("[FCM] 전송 실패 (재시도 처리) - token: {}, reason: {}", message.token(), result.message());
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
}


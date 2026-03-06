package kr.co.yournews.notify.consumer;

import kr.co.yournews.notify.consumer.dto.FcmMessageDto;
import kr.co.yournews.notify.fcm.sender.FcmNotificationSender;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import kr.co.yournews.notify.message.process.service.MessageProcessService;
import kr.co.yournews.notify.token.service.FcmTokenService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class AbstractFcmConsumer {
    protected final FcmNotificationSender fcmNotificationSender;
    protected final FcmTokenService fcmTokenService;
    protected final MessageProcessService processService;

    protected AbstractFcmConsumer(
            FcmNotificationSender fcmNotificationSender,
            FcmTokenService fcmTokenService,
            MessageProcessService processService
    ) {
        this.fcmNotificationSender = fcmNotificationSender;
        this.fcmTokenService = fcmTokenService;
        this.processService = processService;
    }

    /**
     * FCM 전송 호출 메서드
     */
    protected FcmSendResult sendMessage(FcmMessageDto message) {
        return fcmNotificationSender.sendNotification(
                message.token(),
                message.title(),
                message.content(),
                buildMessageData(message.target(), message.info())
        );
    }

    /**
     * FCM 전송 실패 처리 메서드
     * - 일시적인 장애면 처리하지 않고 false
     * - 영구적인 실패면 삭제 후 true
     */
    protected boolean handleInvalidToken(String logPrefix, FcmMessageDto message, String idempotencyKey, FcmSendResult result) {
        if (!result.shouldRemoveToken()) {
            return false;
        }

        fcmTokenService.removeByToken(message.token());
        processService.markFailedPermanent(idempotencyKey, result.message());
        log.warn("[{}] 유효하지 않은 토큰 삭제 - key={}, reason={}", logPrefix, idempotencyKey, result.message());
        return true;
    }

    /**
     * Notification data 생성 메서드
     *
     * @param target : 알림 타입
     * @param info   : 알림 타입별 데이터
     * @return data가 저장된 Map 자료구조
     */
    private Map<String, String> buildMessageData(String target, String info) {
        Map<String, String> data = new HashMap<>();
        data.put("target", target);
        data.put("info", info);
        return data;
    }
}

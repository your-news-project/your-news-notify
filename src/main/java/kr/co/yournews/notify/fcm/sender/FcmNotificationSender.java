package kr.co.yournews.notify.fcm.sender;

import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FcmNotificationSender {

    /**
     * FCM 푸시 알림을 전송하고 결과를 반환하는 메서드
     *
     * @param token   : 수신자의 FCM 디바이스 토큰
     * @param title   : 알림 제목
     * @param content : 알림 내용
     * @return FCM 전송 결과 객체
     */
    public FcmSendResult sendNotification(String token, String title, String content, Map<String, String> data) {
        Message message = buildMessage(token, title, content, data);

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            return FcmSendResult.success(response);
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();

            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                return FcmSendResult.invalidToken(e.getMessage());
            }

            return FcmSendResult.failure(e.getMessage());
        }
    }

    /**
     * 주어진 정보로 FCM 메시지를 생성 메서드
     *
     * @param token   : 수신자 디바이스 토큰
     * @param title   : 알림 제목
     * @param content : 알림 내용
     * @param data    : 추가 데이터
     * @return 구성된 Message 객체
     */
    private Message buildMessage(String token, String title, String content, Map<String, String> data) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(content)
                        .build())
                .putAllData(data)
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound("default").build())
                        .build())
                .build();
    }
}

package kr.co.yournews.notify.fcm.sender;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import kr.co.yournews.notify.fcm.sender.response.FcmSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FcmNotificationSenderTest {

    @Test
    @DisplayName("성공: success=true, shouldRemoveToken=false")
    void sendNotificationSuccess() throws Exception {
        FcmNotificationSender sender = new FcmNotificationSender();
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);

        try (MockedStatic<FirebaseMessaging> mocked = Mockito.mockStatic(FirebaseMessaging.class)) {
            mocked.when(FirebaseMessaging::getInstance).thenReturn(messaging);
            when(messaging.send(any(Message.class))).thenReturn("projects/x/messages/abc123");

            FcmSendResult result = sender.sendNotification("tok", "title", "content", Map.of());

            assertTrue(result.success());
            assertFalse(result.shouldRemoveToken());
            assertTrue(result.message().contains("abc123"));
            verify(messaging, times(1)).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("유효하지 않은 토큰 : UNREGISTERED → invalidToken 처리(shouldRemoveToken=true)")
    void sendNotificationInvalidTokenUnregistered() throws Exception {
        FcmNotificationSender sender = new FcmNotificationSender();
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);

        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(ex.getMessage()).thenReturn("unregistered token");

        try (MockedStatic<FirebaseMessaging> mocked = Mockito.mockStatic(FirebaseMessaging.class)) {
            mocked.when(FirebaseMessaging::getInstance).thenReturn(messaging);
            when(messaging.send(any(Message.class))).thenThrow(ex);

            FcmSendResult result = sender.sendNotification("token", "title", "content", Map.of());

            assertFalse(result.success());
            assertTrue(result.shouldRemoveToken());
            assertTrue(result.message().contains("unregistered"));
            verify(messaging).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("유효하지 않은 토큰: INVALID_ARGUMENT → invalidToken 처리(shouldRemoveToken=true)")
    void sendNotificationInvalidTokenInvalidArgument() throws Exception {
        FcmNotificationSender sender = new FcmNotificationSender();
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);

        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(ex.getMessage()).thenReturn("invalid argument");

        try (MockedStatic<FirebaseMessaging> mocked = Mockito.mockStatic(FirebaseMessaging.class)) {
            mocked.when(FirebaseMessaging::getInstance).thenReturn(messaging);
            when(messaging.send(any(Message.class))).thenThrow(ex);

            FcmSendResult result = sender.sendNotification("token", "title", "content", Map.of());

            assertFalse(result.success());
            assertTrue(result.shouldRemoveToken());
            assertTrue(result.message().contains("invalid"));
            verify(messaging).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("일반 실패: 기타 에러코드 → failure 처리(shouldRemoveToken=false)")
    void sendNotificationFailure() throws Exception {
        FcmNotificationSender sender = new FcmNotificationSender();
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);

        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INTERNAL);
        when(ex.getMessage()).thenReturn("internal error");

        try (MockedStatic<FirebaseMessaging> mocked = Mockito.mockStatic(FirebaseMessaging.class)) {
            mocked.when(FirebaseMessaging::getInstance).thenReturn(messaging);
            when(messaging.send(any(Message.class))).thenThrow(ex);

            FcmSendResult result = sender.sendNotification("token", "title", "content", Map.of());

            assertFalse(result.success());
            assertFalse(result.shouldRemoveToken());
            assertTrue(result.message().contains("internal"));
            verify(messaging).send(any(Message.class));
        }
    }
}

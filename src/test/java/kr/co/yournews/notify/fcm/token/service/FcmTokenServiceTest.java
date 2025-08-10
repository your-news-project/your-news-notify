package kr.co.yournews.notify.fcm.token.service;

import kr.co.yournews.notify.fcm.token.repository.FcmTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class FcmTokenServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @InjectMocks
    private FcmTokenService fcmTokenService;

    private final String token = "fcm-token";

    @Test
    @DisplayName("토큰 삭제 성공")
    void removeByTokenSuccess() {
        // given

        // when
        fcmTokenService.removeByToken(token);

        // then
        verify(fcmTokenRepository, times(1)).deleteByToken(token);
    }

    @Test
    @DisplayName("토큰 삭제 실패 - DB 에러")
    void removeByTokenFailedByDB() {
        // given
        doThrow(new RuntimeException("db error"))
                .when(fcmTokenRepository).deleteByToken(token);

        // when & then
        assertThrows(RuntimeException.class, () -> fcmTokenService.removeByToken(token));
    }
}

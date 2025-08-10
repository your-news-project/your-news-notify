package kr.co.yournews.notify.fcm.token.service;

import kr.co.yournews.notify.fcm.token.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmTokenService {
    private final FcmTokenRepository fcmTokenRepository;

    public void removeByToken(String token) {
        fcmTokenRepository.deleteByToken(token);
        log.info("[FCM 토큰 삭제 완료] token : {}", token);
    }
}

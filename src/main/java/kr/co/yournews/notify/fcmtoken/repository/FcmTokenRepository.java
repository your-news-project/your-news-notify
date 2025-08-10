package kr.co.yournews.notify.fcmtoken.repository;

public interface FcmTokenRepository {
    void deleteByToken(String token);
}

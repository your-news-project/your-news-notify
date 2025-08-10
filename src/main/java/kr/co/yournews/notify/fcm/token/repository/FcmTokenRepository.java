package kr.co.yournews.notify.fcm.token.repository;

public interface FcmTokenRepository {
    void deleteByToken(String token);
}

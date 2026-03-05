package kr.co.yournews.notify.token.repository;

public interface FcmTokenRepository {
    void deleteByToken(String token);
}

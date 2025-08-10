package kr.co.yournews.notify.fcm.token.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcFcmTokenRepository implements FcmTokenRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void deleteByToken(String token) {
        jdbcTemplate.update("DELETE FROM fcm_token WHERE token = ?", token);
    }
}

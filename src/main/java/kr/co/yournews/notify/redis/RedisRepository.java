package kr.co.yournews.notify.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisRepository {
    private final StringRedisTemplate redisTemplate;

    /**
     * 최초 처리 선점
     * - true: 해당 컨슈머가 처음 잡음 (처리 진행)
     * - false: 이미 다른 곳에서 처리 중이거나 완료됨 (중복)
     */
    public boolean tryBegin(String key, Duration processingTtl) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(
                        key,
                        MessageStatus.PROCESSING.name(),
                        processingTtl
                )
        );
    }

    /**
     * 처리 완료 마킹 (중복 처리 방지)
     */
    public void markDone(String key, Duration doneTtl) {
        redisTemplate.opsForValue().set(
                key,
                MessageStatus.DONE.name(),
                doneTtl);
    }

    /**
     * 실패 시 처리 중 키 제거 (다시 처리)
     */
    public void clear(String key) {
        redisTemplate.delete(key);
    }
}

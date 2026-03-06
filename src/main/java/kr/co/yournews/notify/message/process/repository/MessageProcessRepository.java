package kr.co.yournews.notify.message.process.repository;

import kr.co.yournews.notify.message.process.model.MessageProcess;

import java.util.Optional;

public interface MessageProcessRepository {
    MessageProcess claim(String idempotencyKey, String tokenHash, int maxAttemptCount);

    void markSuccess(String idempotencyKey);

    void markRetryPending(String idempotencyKey, String errorMessage);

    void markFailedRetryExhausted(String idempotencyKey, String errorMessage);

    void markFailedPermanent(String idempotencyKey, String errorMessage);

    void markFailedFinal(String idempotencyKey, String errorMessage);

    Optional<MessageProcess> findByIdempotencyKey(String idempotencyKey);

    void increaseDlqAttempt(String idempotencyKey);
}

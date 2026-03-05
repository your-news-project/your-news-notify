package kr.co.yournews.notify.message.process.repository;

import kr.co.yournews.notify.message.process.model.MessageProcessClaim;

public interface MessageProcessRepository {
    MessageProcessClaim claim(String idempotencyKey, String tokenHash, int maxAttemptCount);

    void markSuccess(String idempotencyKey);

    void markRetryPending(String idempotencyKey, String errorMessage);

    void markFailedRetryExhausted(String idempotencyKey, String errorMessage);

    void markFailedPermanent(String idempotencyKey, String errorMessage);
}

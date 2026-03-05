package kr.co.yournews.notify.message.process.service;

import kr.co.yournews.notify.message.process.model.MessageProcessClaim;
import kr.co.yournews.notify.message.process.repository.MessageProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageProcessService {
    private final MessageProcessRepository fcmMessageProcessRepository;

    public MessageProcessClaim claim(String idempotencyKey, String tokenHash, int maxAttemptCount) {
        return fcmMessageProcessRepository.claim(idempotencyKey, tokenHash, maxAttemptCount);
    }

    public void markSuccess(String idempotencyKey) {
        fcmMessageProcessRepository.markSuccess(idempotencyKey);
    }

    public void markRetryPending(String idempotencyKey, String errorMessage) {
        fcmMessageProcessRepository.markRetryPending(idempotencyKey, errorMessage);
    }

    public void markFailedRetryExhausted(String idempotencyKey, String errorMessage) {
        fcmMessageProcessRepository.markFailedRetryExhausted(idempotencyKey, errorMessage);
    }

    public void markFailedPermanent(String idempotencyKey, String errorMessage) {
        fcmMessageProcessRepository.markFailedPermanent(idempotencyKey, errorMessage);
    }
}

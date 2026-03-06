package kr.co.yournews.notify.message.process.service;

import kr.co.yournews.notify.message.process.model.MessageProcess;
import kr.co.yournews.notify.message.process.repository.MessageProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MessageProcessService {
    private final MessageProcessRepository fcmMessageProcessRepository;

    public MessageProcess claim(String idempotencyKey, String tokenHash, int maxAttemptCount) {
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

    public void markFailedFinal(String idempotencyKey, String errorMessage) {
        fcmMessageProcessRepository.markFailedFinal(idempotencyKey, errorMessage);
    }

    public Optional<MessageProcess> findByIdempotencyKey(String idempotencyKey) {
        return fcmMessageProcessRepository.findByIdempotencyKey(idempotencyKey);
    }

    public void increaseDlqAttempt(String idempotencyKey) {
        fcmMessageProcessRepository.increaseDlqAttempt(idempotencyKey);
    }
}

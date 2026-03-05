package kr.co.yournews.notify.message.process.repository;

import kr.co.yournews.notify.message.process.model.MessageProcessStatus;
import kr.co.yournews.notify.message.process.model.MessageProcessClaim;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class JdbcMessageProcessRepository implements MessageProcessRepository {
    private final JdbcTemplate jdbcTemplate;

    /**
     * 메시지 처리권(claim) 획득 메서드
     * - 최초 수신이면 INSERT로 PROCESSING 상태 생성 후 claim 성공
     * - 기존 키면 RETRY_PENDING -> PROCESSING 전이를 시도
     * - 둘 다 실패하면 현재 상태를 조회해 skip 사유 반환
     *
     * @param idempotencyKey  : 멱등 키
     * @param tokenHash       : 토큰 해시
     * @param maxAttemptCount : 최대 재시도 횟수
     * @return : claim 성공/실패(스킵) 결과
     */
    @Override
    public MessageProcessClaim claim(
            String idempotencyKey,
            String tokenHash,
            int maxAttemptCount
    ) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp nowTs = Timestamp.valueOf(now);

        try {
            // 최초 수신이면 새 행을 만들고 바로 처리권 획득
            int inserted = jdbcTemplate.update(
                    """
                            INSERT INTO message_process (
                                idempotency_key,
                                token_hash,
                                status,
                                attempt_count,
                                max_attempt_count,
                                processing_started_at,
                                completed_at,
                                last_error_code,
                                last_error_message,
                                created_at,
                                updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    idempotencyKey,
                    tokenHash,
                    MessageProcessStatus.PROCESSING.name(),
                    1,
                    maxAttemptCount,
                    nowTs,
                    null,
                    null,
                    null,
                    nowTs,
                    nowTs
            );
            if (inserted == 1) {
                return MessageProcessClaim.claimed(1);
            }
        } catch (DuplicateKeyException ignore) {
            // 이미 있는 키면 아래 전이(재처리 가능 상태인지 확인)로 진행
        }

        // RETRY_PENDING 상태일 때만 다시 PROCESSING으로 전이
        int resumed = jdbcTemplate.update(
                """
                        UPDATE message_process
                        SET status = ?,
                            attempt_count = attempt_count + 1,
                            processing_started_at = ?,
                            completed_at = NULL,
                            last_error_code = NULL,
                            last_error_message = NULL,
                            token_hash = ?,
                            updated_at = ?
                        WHERE idempotency_key = ?
                          AND status = ?
                          AND attempt_count < max_attempt_count
                        """,
                MessageProcessStatus.PROCESSING.name(),
                nowTs,
                tokenHash,
                nowTs,
                idempotencyKey,
                MessageProcessStatus.RETRY_PENDING.name()
        );

        if (resumed == 1) {
            // 전이 성공 시 현재 attempt_count 재조회 후 claim 성공 반환
            Integer attempt = jdbcTemplate.queryForObject(
                    "SELECT attempt_count FROM message_process WHERE idempotency_key = ?",
                    Integer.class,
                    idempotencyKey
            );
            return MessageProcessClaim.claimed(attempt == null ? 1 : attempt);
        }

        //  현재 상태 조회 후 skip
        MessageProcessClaim claim = jdbcTemplate.query(
                """
                        SELECT status, attempt_count
                        FROM message_process
                        WHERE idempotency_key = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    return MessageProcessClaim.skipped(
                            rs.getInt("attempt_count"),
                            MessageProcessStatus.valueOf(rs.getString("status"))
                    );
                },
                idempotencyKey
        );

        if (claim == null) {
            return MessageProcessClaim.skipped(0, MessageProcessStatus.RETRY_PENDING);
        }
        return claim;
    }

    /**
     * PROCESSING 상태 메시지를 성공(SUCCEEDED) 처리 메서드
     *
     * @param idempotencyKey : 멱등 키
     */
    @Override
    public void markSuccess(String idempotencyKey) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp nowTs = Timestamp.valueOf(now);
        jdbcTemplate.update(
                """
                        UPDATE message_process
                        SET status = ?,
                            completed_at = ?,
                            last_error_code = NULL,
                            last_error_message = NULL,
                            updated_at = ?
                        WHERE idempotency_key = ?
                          AND status = ?
                        """,
                MessageProcessStatus.SUCCEEDED.name(),
                nowTs,
                nowTs,
                idempotencyKey,
                MessageProcessStatus.PROCESSING.name()
        );
    }

    /**
     * PROCESSING 상태 메시지를 재시도 대기(RETRY_PENDING) 처리 메서드
     *
     * @param idempotencyKey : 멱등 키
     * @param errorMessage   : 마지막 에러 메시지
     */
    @Override
    public void markRetryPending(String idempotencyKey, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp nowTs = Timestamp.valueOf(now);
        jdbcTemplate.update(
                """
                        UPDATE message_process
                        SET status = ?,
                            completed_at = NULL,
                            last_error_code = ?,
                            last_error_message = ?,
                            updated_at = ?
                        WHERE idempotency_key = ?
                          AND status = ?
                        """,
                MessageProcessStatus.RETRY_PENDING.name(),
                "RETRYABLE_SEND_FAIL",
                errorMessage,
                nowTs,
                idempotencyKey,
                MessageProcessStatus.PROCESSING.name()
        );
    }

    /**
     * 최대 재시도 초과 실패 상태 처리 메서드
     *
     * @param idempotencyKey : 멱등 키
     * @param errorMessage   : 마지막 에러 메시지
     */
    @Override
    public void markFailedRetryExhausted(String idempotencyKey, String errorMessage) {
        markFailed(
                idempotencyKey,
                MessageProcessStatus.FAILED_RETRY_EXHAUSTED,
                "MAX_RETRY_EXHAUSTED",
                errorMessage
        );
    }

    /**
     * 비재시도(영구 실패) 상태로 처리 메서드
     *
     * @param idempotencyKey : 멱등 키
     * @param errorMessage   : 마지막 에러 메시지
     */
    @Override
    public void markFailedPermanent(String idempotencyKey, String errorMessage) {
        markFailed(
                idempotencyKey,
                MessageProcessStatus.FAILED_PERMANENT,
                "PERMANENT_SEND_FAIL",
                errorMessage
        );
    }

    /**
     * 실패 상태 전이 처리 메서드
     * - PROCESSING 또는 RETRY_PENDING 상태에서만 실패 상태로 전이
     *
     * @param idempotencyKey : 멱등 키
     * @param status         : 최종 실패 상태
     * @param errorCode      : 에러 코드
     * @param errorMessage   : 에러 메시지
     */
    private void markFailed(
            String idempotencyKey,
            MessageProcessStatus status,
            String errorCode,
            String errorMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp nowTs = Timestamp.valueOf(now);
        jdbcTemplate.update(
                """
                        UPDATE message_process
                        SET status = ?,
                            completed_at = ?,
                            last_error_code = ?,
                            last_error_message = ?,
                            updated_at = ?
                        WHERE idempotency_key = ?
                          AND status IN (?, ?)
                        """,
                status.name(),
                nowTs,
                errorCode,
                errorMessage,
                nowTs,
                idempotencyKey,
                MessageProcessStatus.PROCESSING.name(),
                MessageProcessStatus.RETRY_PENDING.name()
        );
    }
}

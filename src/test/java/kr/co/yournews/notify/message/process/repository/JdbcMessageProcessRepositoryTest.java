package kr.co.yournews.notify.message.process.repository;

import kr.co.yournews.notify.message.process.model.MessageProcess;
import kr.co.yournews.notify.message.process.model.MessageProcessStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcMessageProcessRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private JdbcMessageProcessRepository jdbcMessageProcessRepository;

    private static final String IDEMPOTENCY_KEY = "idemp-key";
    private static final String TOKEN_HASH = "token-hash";
    private static final int MAX_ATTEMPT = 3;

    @Test
    @DisplayName("claim - 최초 INSERT 성공 시 claimed(true) 반환")
    void claimFirstInsertSuccess() {
        // given
        when(jdbcTemplate.update(contains("INSERT INTO message_process"), any(Object[].class)))
                .thenReturn(1);

        // when
        MessageProcess claim = jdbcMessageProcessRepository.claim(IDEMPOTENCY_KEY, TOKEN_HASH, MAX_ATTEMPT);

        // then
        assertTrue(claim.claimed());
        assertEquals(1, claim.attemptCount());
        assertEquals(MessageProcessStatus.PROCESSING, claim.status());
    }

    @Test
    @DisplayName("claim - 중복 INSERT 이후 RETRY_PENDING 재선점 성공 시 claimed(true) 반환")
    void claimResumeSuccessAfterDuplicateInsert() {
        // given
        when(jdbcTemplate.update(contains("INSERT INTO message_process"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbcTemplate.update(contains("UPDATE message_process"), any(Object[].class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                contains("SELECT attempt_count FROM message_process"),
                eq(Integer.class),
                eq(IDEMPOTENCY_KEY)
        )).thenReturn(2);

        // when
        MessageProcess claim = jdbcMessageProcessRepository.claim(IDEMPOTENCY_KEY, TOKEN_HASH, MAX_ATTEMPT);

        // then
        assertTrue(claim.claimed());
        assertEquals(2, claim.attemptCount());
        assertEquals(MessageProcessStatus.PROCESSING, claim.status());
    }

    @Test
    @DisplayName("claim - 재선점 실패 시 현재 상태 조회 결과를 skipped로 반환")
    void claimSkipByExistingStatus() {
        // given
        MessageProcess existing = MessageProcess.skipped(3, MessageProcessStatus.SUCCEEDED);

        when(jdbcTemplate.update(contains("INSERT INTO message_process"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbcTemplate.update(contains("UPDATE message_process"), any(Object[].class)))
                .thenReturn(0);
        when(jdbcTemplate.query(
                contains("SELECT status, attempt_count"),
                any(ResultSetExtractor.class),
                eq(IDEMPOTENCY_KEY)
        )).thenReturn(existing);

        // when
        MessageProcess claim = jdbcMessageProcessRepository.claim(IDEMPOTENCY_KEY, TOKEN_HASH, MAX_ATTEMPT);

        // then
        assertFalse(claim.claimed());
        assertEquals(3, claim.attemptCount());
        assertEquals(MessageProcessStatus.SUCCEEDED, claim.status());
    }

    @Test
    @DisplayName("claim - 재선점 실패 + 상태 미존재면 skipped(RETRY_PENDING) 반환")
    void claimSkipWhenNoExistingRow() {
        // given
        when(jdbcTemplate.update(contains("INSERT INTO message_process"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(jdbcTemplate.update(contains("UPDATE message_process"), any(Object[].class)))
                .thenReturn(0);
        when(jdbcTemplate.query(
                contains("SELECT status, attempt_count"),
                any(ResultSetExtractor.class),
                eq(IDEMPOTENCY_KEY)
        )).thenReturn(null);

        // when
        MessageProcess claim = jdbcMessageProcessRepository.claim(IDEMPOTENCY_KEY, TOKEN_HASH, MAX_ATTEMPT);

        // then
        assertFalse(claim.claimed());
        assertEquals(0, claim.attemptCount());
        assertEquals(MessageProcessStatus.RETRY_PENDING, claim.status());
    }

    @Test
    @DisplayName("markSuccess - SUCCEEDED 상태로 업데이트")
    void markSuccessUpdate() {
        // given
        when(jdbcTemplate.update(contains("UPDATE message_process"), any(Object[].class)))
                .thenReturn(1);

        // when
        jdbcMessageProcessRepository.markSuccess(IDEMPOTENCY_KEY);

        // then
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(1))
                .update(contains("UPDATE message_process"), captor.capture());
        assertEquals(MessageProcessStatus.SUCCEEDED.name(), captor.getValue()[0]);
    }

    @Test
    @DisplayName("markRetryPending - RETRY_PENDING 상태와 에러코드로 업데이트")
    void markRetryPendingUpdate() {
        // given
        when(jdbcTemplate.update(contains("UPDATE message_process"), any(Object[].class)))
                .thenReturn(1);

        // when
        jdbcMessageProcessRepository.markRetryPending(IDEMPOTENCY_KEY, "retryable-error");

        // then
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(1))
                .update(contains("UPDATE message_process"), captor.capture());
        assertEquals(MessageProcessStatus.RETRY_PENDING.name(), captor.getValue()[0]);
        assertEquals("RETRYABLE_SEND_FAIL", captor.getValue()[1]);
        assertEquals("retryable-error", captor.getValue()[2]);
    }

    @Test
    @DisplayName("markFailedRetryExhausted - FAILED_RETRY_EXHAUSTED 상태로 업데이트")
    void markFailedRetryExhaustedUpdate() {
        // given
        when(jdbcTemplate.update(contains("UPDATE message_process"), any(Object[].class)))
                .thenReturn(1);

        // when
        jdbcMessageProcessRepository.markFailedRetryExhausted(IDEMPOTENCY_KEY, "max-retry");

        // then
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(1))
                .update(contains("UPDATE message_process"), captor.capture());
        assertEquals(MessageProcessStatus.FAILED_RETRY_EXHAUSTED.name(), captor.getValue()[0]);
        assertEquals("MAX_RETRY_EXHAUSTED", captor.getValue()[2]);
    }

    @Test
    @DisplayName("markFailedPermanent - FAILED_PERMANENT 상태로 업데이트")
    void markFailedPermanentUpdate() {
        // given
        when(jdbcTemplate.update(contains("UPDATE message_process"), any(Object[].class)))
                .thenReturn(1);

        // when
        jdbcMessageProcessRepository.markFailedPermanent(IDEMPOTENCY_KEY, "permanent-fail");

        // then
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, times(1))
                .update(contains("UPDATE message_process"), captor.capture());
        assertEquals(MessageProcessStatus.FAILED_PERMANENT.name(), captor.getValue()[0]);
        assertEquals("PERMANENT_SEND_FAIL", captor.getValue()[2]);
    }
}

package kr.co.yournews.notify.message.process.model;

public enum MessageProcessStatus {
    PROCESSING,                 // 처리중
    RETRY_PENDING,              // 재시도 대기
    SUCCEEDED,                  // 처리 성공
    FAILED_RETRY_EXHAUSTED,     // 재시도 횟수 초과
    FAILED_PERMANENT            // 영구적인 실패 (ex. 토큰 존재 x)
}

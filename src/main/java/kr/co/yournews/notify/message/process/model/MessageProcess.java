package kr.co.yournews.notify.message.process.model;

public record MessageProcess(
        boolean claimed,
        int attemptCount,
        MessageProcessStatus status
) {
    public static MessageProcess claimed(int attemptCount) {
        return new MessageProcess(true, attemptCount, MessageProcessStatus.PROCESSING);
    }

    public static MessageProcess skipped(int attemptCount, MessageProcessStatus status) {
        return new MessageProcess(false, attemptCount, status);
    }

    public static MessageProcess loaded(int attemptCount, MessageProcessStatus status) {
        return new MessageProcess(false, attemptCount, status);
    }
}

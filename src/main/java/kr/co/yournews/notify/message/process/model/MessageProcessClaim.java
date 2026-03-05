package kr.co.yournews.notify.message.process.model;

public record MessageProcessClaim(
        boolean claimed,
        int attemptCount,
        MessageProcessStatus status
) {
    public static MessageProcessClaim claimed(int attemptCount) {
        return new MessageProcessClaim(true, attemptCount, MessageProcessStatus.PROCESSING);
    }

    public static MessageProcessClaim skipped(int attemptCount, MessageProcessStatus status) {
        return new MessageProcessClaim(false, attemptCount, status);
    }
}

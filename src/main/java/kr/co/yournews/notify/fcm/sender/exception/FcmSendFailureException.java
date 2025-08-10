package kr.co.yournews.notify.fcm.sender.exception;

public class FcmSendFailureException extends RuntimeException {
    public FcmSendFailureException(String message) {
        super(message);
    }
}

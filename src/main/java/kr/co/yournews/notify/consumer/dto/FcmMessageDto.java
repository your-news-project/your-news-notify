package kr.co.yournews.notify.consumer.dto;

public record FcmMessageDto(
        String token,
        String title,
        String data,
        boolean isFirst,
        boolean isLast
) {
}

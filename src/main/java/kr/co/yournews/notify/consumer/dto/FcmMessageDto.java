package kr.co.yournews.notify.consumer.dto;

public record FcmMessageDto(
        String token,
        String title,
        String content,
        String target,
        String info
) {
}

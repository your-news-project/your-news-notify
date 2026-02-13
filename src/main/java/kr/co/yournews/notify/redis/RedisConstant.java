package kr.co.yournews.notify.redis;

import org.apache.commons.codec.digest.DigestUtils;

public final class RedisConstant {

    private RedisConstant() { }

    private static final String FCM_KEY_FORMAT = "idemp::fcm::%s::%s";

    public static String getKey(String messageId, String token) {
        return String.format(FCM_KEY_FORMAT, messageId, hash(token));
    }

    private static String hash(String token) {
        return DigestUtils.sha256Hex(token).substring(0, 16);
    }
}

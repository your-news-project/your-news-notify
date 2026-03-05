package kr.co.yournews.notify.message.process.util;

import org.apache.commons.codec.digest.DigestUtils;

public final class IdempotencyKeyUtil {

    private IdempotencyKeyUtil() { }

    private static final String FCM_KEY_FORMAT = "idemp::fcm::%s::%s";

    public static String getKey(String businessId, String token) {
        return String.format(FCM_KEY_FORMAT, businessId, hash(token));
    }

    public static String hash(String token) {
        return DigestUtils.sha256Hex(token).substring(0, 16);
    }
}

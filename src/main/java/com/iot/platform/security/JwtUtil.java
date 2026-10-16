package com.iot.platform.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 轻量 HMAC-SHA256 签名令牌（无额外依赖）。
 * 令牌格式：&lt;base64(subject.expireMillis)&gt;.&lt;base64url(HMAC(secret, payload))&gt;
 * 仅用于脚手架鉴权演示，生产请替换为标准 JWT 并实现密钥管理与轮换。
 */
public class JwtUtil {

    private final String secret;

    public JwtUtil(String secret) {
        this.secret = secret;
    }

    public String generate(String subject, long expireMillis) {
        long exp = System.currentTimeMillis() + expireMillis;
        String payload = base64Url(subject + "." + exp);
        return payload + "." + sign(payload);
    }

    public String verify(String token) {
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("empty token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            throw new RuntimeException("bad token");
        }
        if (!parts[1].equals(sign(parts[0]))) {
            throw new RuntimeException("bad signature");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(decoded.split("\\.")[1]);
            if (System.currentTimeMillis() > exp) {
                throw new RuntimeException("token expired");
            }
            return decoded.split("\\.")[0];
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("bad token", e);
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String base64Url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}

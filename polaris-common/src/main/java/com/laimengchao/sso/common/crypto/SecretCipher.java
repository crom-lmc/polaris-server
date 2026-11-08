package com.laimengchao.sso.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置密文工具：{@code ENC(base64)} 形式的 AES-GCM 加解密。
 * <p>
 * 用法约定：
 * <ul>
 *   <li>Nacos 里存放的是密文，形如 {@code ENC(3q2+7w...) }；</li>
 *   <li>主密钥只通过环境变量 {@code SSO_CONF_KEY} 提供，既不进 Nacos 也不进代码仓库；</li>
 *   <li>服务启动时由 {@link EncryptedPropertyEnvironmentPostProcessor} 统一解密，
 *       业务代码拿到的永远是明文，不需要感知加密的存在。</li>
 * </ul>
 * 密文结构：{@code base64(iv[12] || ciphertext || tag[16])}，每次加密随机 IV，
 * 因此同一明文两次加密结果不同——不会因为密文相同而泄露「两个环境用了同一个口令」这类信息。
 * <p>
 * 只依赖 JDK 自带的 JCE，不引入第三方加密库。
 */
public final class SecretCipher {

    /** 密文前缀，与解密器的识别规则保持一致 */
    public static final String PREFIX = "ENC(";
    /** 密文后缀 */
    public static final String SUFFIX = ")";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    /** GCM 推荐 96 位 IV */
    private static final int IV_BYTES = 12;
    /** 认证标签 128 位，完整性校验一并完成 */
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretCipher() {
    }

    /** 判断某个配置值是否为密文（必须整串都是 ENC(...)，不做前缀误判）。 */
    public static boolean isEncrypted(String value) {
        return value != null
                && value.startsWith(PREFIX)
                && value.endsWith(SUFFIX)
                && value.length() > PREFIX.length() + SUFFIX.length();
    }

    /** 加密明文，返回 {@code ENC(...)}。 */
    public static String encrypt(String plain, byte[] key) {
        if (plain == null) {
            throw new IllegalArgumentException("待加密内容不能为 null");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload) + SUFFIX;
        } catch (Exception e) {
            throw new IllegalStateException("配置加密失败：" + e.getMessage(), e);
        }
    }

    /** 解密 {@code ENC(...)}；传入非密文时原样返回，便于无脑调用。 */
    public static String decrypt(String value, byte[] key) {
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            byte[] payload = Base64.getDecoder()
                    .decode(value.substring(PREFIX.length(), value.length() - SUFFIX.length()).trim());
            if (payload.length <= IV_BYTES) {
                throw new IllegalArgumentException("密文长度不足");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "配置解密失败：主密钥与密文不匹配，或密文被截断/篡改（" + e.getMessage() + "）", e);
        }
    }

    /**
     * 解析主密钥：优先按 Base64 解码（推荐，{@code java tools/SecretTool.java genkey} 生成），
     * 解码后长度不是 16/24/32 时回退为原始 UTF-8 字节，两者都不合法则直接报错。
     */
    public static byte[] parseKey(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("主密钥为空");
        }
        String text = raw.trim();
        byte[] decoded = null;
        try {
            decoded = Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException ignore) {
            // 不是合法 Base64，走原始字符分支
        }
        if (decoded != null && isValidKeyLength(decoded.length)) {
            return decoded;
        }
        byte[] rawBytes = text.getBytes(StandardCharsets.UTF_8);
        if (isValidKeyLength(rawBytes.length)) {
            return rawBytes;
        }
        throw new IllegalStateException("主密钥长度非法（Base64 解码后 " + (decoded == null ? "-" : decoded.length)
                + " 字节，原始 " + rawBytes.length + " 字节），AES 要求 16/24/32 字节");
    }

    private static boolean isValidKeyLength(int len) {
        return len == 16 || len == 24 || len == 32;
    }
}

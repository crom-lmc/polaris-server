package com.laimengchao.sso.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PEM 密钥解析。JWT 的 RS256 密钥对由 openssl 生成：
 * <pre>
 * openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
 * openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
 * </pre>
 * 只依赖 JDK，不引第三方 crypto 库。
 */
public final class PemUtils {

    private PemUtils() {
    }

    /** 解析 PKCS#8 私钥（-----BEGIN PRIVATE KEY-----）。 */
    public static PrivateKey parsePrivateKey(InputStream in) {
        try {
            byte[] der = readDer(in, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("解析 RSA 私钥失败: " + e.getMessage(), e);
        }
    }

    /** 解析 X.509 公钥（-----BEGIN PUBLIC KEY-----）。 */
    public static PublicKey parsePublicKey(InputStream in) {
        try {
            byte[] der = readDer(in, "PUBLIC KEY");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("解析 RSA 公钥失败: " + e.getMessage(), e);
        }
    }

    /** 去掉 PEM 头尾与换行，取 Base64 正文再解码。 */
    private static byte[] readDer(InputStream in, String type) throws IOException {
        String pem = readAll(in)
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}

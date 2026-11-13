package com.laimengchao.sso.common.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 上传文件扩展名与魔数校验，并返回服务端认可的 MIME。 */
public final class FileTypeValidator {

    private static final int HEADER_SIZE = 32;
    private static final Set<String> ACTIVE_CONTENT = Set.of(
            "svg", "html", "htm", "xhtml", "xml", "js", "mjs", "jsp", "jspx",
            "php", "asp", "aspx", "exe", "dll", "bat", "cmd", "sh", "jar");
    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "ico");
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("zip", "application/zip"));

    private FileTypeValidator() {
    }

    /**
     * 从流头读取少量字节并复位，调用方随后仍可完整保存文件。
     * 输入流必须支持 mark/reset，通常由调用方包装为 BufferedInputStream。
     */
    public static String validate(InputStream input, String extension) throws IOException {
        String ext = normalize(extension);
        if (ACTIVE_CONTENT.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不允许上传可执行或主动内容文件");
        }
        String mimeType = MIME_TYPES.get(ext);
        if (mimeType == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的文件类型");
        }
        if (input == null || !input.markSupported()) {
            throw new IllegalArgumentException("文件校验需要支持 mark/reset 的输入流");
        }
        input.mark(HEADER_SIZE);
        byte[] header = input.readNBytes(HEADER_SIZE);
        input.reset();
        if (!matches(ext, header)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件内容与扩展名不匹配");
        }
        return mimeType;
    }

    public static boolean isImageFilename(String filename) {
        if (filename == null) {
            return false;
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && IMAGES.contains(normalize(filename.substring(dot + 1)));
    }

    private static String normalize(String extension) {
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String ext, byte[] h) {
        return switch (ext) {
            case "jpg", "jpeg" -> startsWith(h, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> ascii(h, 0, "GIF87a") || ascii(h, 0, "GIF89a");
            case "webp" -> ascii(h, 0, "RIFF") && ascii(h, 8, "WEBP");
            case "bmp" -> ascii(h, 0, "BM");
            case "ico" -> startsWith(h, 0x00, 0x00, 0x01, 0x00);
            case "pdf" -> ascii(h, 0, "%PDF-");
            case "mp4" -> ascii(h, 4, "ftyp");
            case "mp3" -> ascii(h, 0, "ID3") || isMp3Frame(h);
            case "zip" -> startsWith(h, 0x50, 0x4B, 0x03, 0x04)
                    || startsWith(h, 0x50, 0x4B, 0x05, 0x06)
                    || startsWith(h, 0x50, 0x4B, 0x07, 0x08);
            default -> false;
        };
    }

    private static boolean isMp3Frame(byte[] h) {
        if (h.length < 4 || (h[0] & 0xFF) != 0xFF || (h[1] & 0xE0) != 0xE0) {
            return false;
        }
        int version = (h[1] >> 3) & 0x03;
        int layer = (h[1] >> 1) & 0x03;
        return version != 1 && layer != 0;
    }

    private static boolean ascii(byte[] bytes, int offset, String expected) {
        if (offset < 0 || bytes.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if ((bytes[offset + i] & 0xFF) != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}

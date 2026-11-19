package com.laimengchao.sso.forum.storage;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.FileTypeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
public class ForumStorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final Path root;

    public ForumStorageService(@Value("${forum.upload.root:./uploads/forum}") String uploadRoot) {
        this.root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建论坛上传目录", e);
        }
    }

    public StoredFile store(MultipartFile file, String extension) {
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path directory = resolveSafe(root.resolve(LocalDate.now().format(DATE_PATH)));
        Path target = resolveSafe(directory.resolve(fileName));
        try {
            Files.createDirectories(directory);
            String mimeType;
            try (InputStream input = new BufferedInputStream(file.getInputStream())) {
                mimeType = FileTypeValidator.validate(input, extension);
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredFile(root.relativize(target).toString().replace("\\", "/"), mimeType, Files.size(target));
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件保存失败");
        }
    }

    public Resource read(String relativePath) {
        try {
            Path path = resolveSafe(root.resolve(relativePath));
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BizException(ErrorCode.NOT_FOUND, "附件文件不存在");
            }
            return resource;
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "附件读取失败");
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Files.deleteIfExists(resolveSafe(root.resolve(relativePath)));
        } catch (IOException ignored) {
            // 数据库事务失败时尽力清理尚未绑定的文件。
        }
    }

    private Path resolveSafe(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new BizException(ErrorCode.PARAM_ERROR, "非法文件路径");
        return normalized;
    }

    public static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record StoredFile(String storagePath, String mimeType, long size) {
    }
}

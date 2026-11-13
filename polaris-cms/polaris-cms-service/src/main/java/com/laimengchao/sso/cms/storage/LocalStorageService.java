package com.laimengchao.sso.cms.storage;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.FileTypeValidator;
import com.laimengchao.sso.cms.common.CmsErrorCode;
import com.laimengchao.sso.cms.config.CmsProperties;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 本地磁盘存储。重命名存储防覆盖与路径穿越；按日期分目录。
 */
@Slf4j
@Service
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final CmsProperties properties;
    private final Path root;
    private final String urlPrefix;

    public LocalStorageService(CmsProperties properties,
                               @org.springframework.beans.factory.annotation.Value("${cms.upload.root:./uploads-cms}") String uploadRoot,
                               @org.springframework.beans.factory.annotation.Value("${cms.upload.url-prefix:/api/cms/media/file}") String urlPrefix) {
        this.properties = properties;
        this.root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix.endsWith("/") ? urlPrefix.substring(0, urlPrefix.length() - 1) : urlPrefix;
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录：" + this.root, e);
        }
    }

    @Override
    public StoredFile store(MultipartFile file, String folder) throws IOException {
        return store(file.getInputStream(), file.getOriginalFilename(), file.getContentType(),
                file.getSize(), folder);
    }

    @Override
    public StoredFile store(InputStream inputStream, String originalName, String contentType,
                            long size, String folder) throws IOException {
        checkExtension(originalName);
        long max = properties.getUpload().getMaxSizeMb() * 1024L * 1024L;
        if (size > max) {
            throw new BizException(CmsErrorCode.FILE_TOO_LARGE);
        }
        String ext = extension(originalName);
        String fileName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        String dateDir = LocalDate.now().format(DATE_PATH);
        Path dir = root.resolve(dateDir).normalize();
        if (!dir.startsWith(root)) {
            throw new BizException(CmsErrorCode.UPLOAD_FAILED, "非法的存储路径");
        }
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        String validatedContentType;
        try (InputStream in = new BufferedInputStream(inputStream)) {
            validatedContentType = FileTypeValidator.validate(in, ext);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        StoredFile sf = new StoredFile();
        sf.setFilename(fileName);
        sf.setOriginalName(originalName);
        sf.setContentType(validatedContentType);
        sf.setSize(size);
        sf.setUrl(urlPrefix + "/" + dateDir + "/" + fileName);
        return sf;
    }

    @Override
    public void delete(String url) {
        try {
            String prefix = urlPrefix + "/";
            if (url == null || !url.startsWith(prefix)) {
                return;
            }
            Path target = root.resolve(url.substring(prefix.length())).normalize();
            if (target.startsWith(root) && Files.exists(target)) {
                Files.deleteIfExists(target);
            }
        } catch (Exception e) {
            // 磁盘文件删除失败不阻断业务，记录即可（库里已逻辑删除）
            log.warn("删除存储文件失败：{}", url, e);
        }
    }

    private void checkExtension(String originalName) {
        String ext = extension(originalName);
        if (ext.isEmpty() || !properties.getUpload().getAllowedExtensions().contains(ext)) {
            throw new BizException(CmsErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}

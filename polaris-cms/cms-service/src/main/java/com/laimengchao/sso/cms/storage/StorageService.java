package com.laimengchao.sso.cms.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/** 存储抽象：当前本地磁盘实现，后续可换 OSS/MinIO */
public interface StorageService {

    StoredFile store(MultipartFile file, String folder) throws IOException;

    StoredFile store(InputStream inputStream, String originalName, String contentType,
                     long size, String folder) throws IOException;

    /** 删除存储文件（不存在时静默） */
    void delete(String url);
}

package com.laimengchao.sso.cms.storage;

import lombok.Data;

import java.time.LocalDateTime;

/** 存储结果 */
@Data
public class StoredFile {

    /** 重命名后的存储文件名（UUID 防穿越） */
    private String filename;
    private String originalName;
    private String contentType;
    private long size;
    /** 访问 URL（url-prefix + /yyyy/MM/dd/ + filename） */
    private String url;
    private LocalDateTime createdAt = LocalDateTime.now();
}

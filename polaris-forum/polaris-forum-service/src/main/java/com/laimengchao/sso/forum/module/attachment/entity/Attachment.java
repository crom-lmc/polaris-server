package com.laimengchao.sso.forum.module.attachment.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class Attachment {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerAccountId;
    private String usageType;
    private String originalName;
    private String storagePath;
    private String mimeType;
    private Long size;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long threadId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long draftId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long profileAccountId;
    private String createdAt;
}

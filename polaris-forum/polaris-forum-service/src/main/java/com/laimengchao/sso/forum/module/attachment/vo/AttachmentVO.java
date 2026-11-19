package com.laimengchao.sso.forum.module.attachment.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class AttachmentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String originalName;
    private String mimeType;
    private Long size;
    private String usageType;
    private String url;
}

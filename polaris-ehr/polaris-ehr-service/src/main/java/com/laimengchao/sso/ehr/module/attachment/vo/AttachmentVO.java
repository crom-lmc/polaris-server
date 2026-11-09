package com.laimengchao.sso.ehr.module.attachment.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class AttachmentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String fileName;
    private Long size;
    private String mime;
}

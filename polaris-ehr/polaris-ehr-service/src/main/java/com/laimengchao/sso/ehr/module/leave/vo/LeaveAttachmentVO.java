package com.laimengchao.sso.ehr.module.leave.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 请假证明材料视图 */
@Data
public class LeaveAttachmentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long attachmentId;

    private String fileName;
}

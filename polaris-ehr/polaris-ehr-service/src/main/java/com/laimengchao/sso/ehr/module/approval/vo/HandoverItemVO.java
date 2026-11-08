package com.laimengchao.sso.ehr.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 交接项目视图 */
@Data
public class HandoverItemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 所属员工ID（被交接的离职/减员员工） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    /** 所属离职/减员申请ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applyId;
    /** 序号，从 1 开始 */
    private Integer seq;
    private String content;
    /** 0 待确认 / 1 已确认 */
    private Integer status;
    private String statusName;
    private String note;
    private String confirmedAt;
}

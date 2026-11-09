package com.laimengchao.sso.ehr.module.leave.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/** 额度流水视图 */
@Data
public class QuotaLogVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long leaveId;
    private String action;
    private String actionName;
    private BigDecimal days;
    private BigDecimal beforeAvailable;
    private BigDecimal afterAvailable;
    private String remark;
    private String createdAt;
}

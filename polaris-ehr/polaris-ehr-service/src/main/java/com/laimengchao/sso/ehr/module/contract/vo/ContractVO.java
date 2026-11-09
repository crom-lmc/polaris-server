package com.laimengchao.sso.ehr.module.contract.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 合同列表 / 详情展示对象。 */
@Data
public class ContractVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String employeeName;
    private String contractNo;
    private String contractType;
    private String contractTypeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate signDate;
    private String status;
    private String statusName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long renewFromId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;
    private String fileName;
    private String remark;
    /** 距到期天数：负数=已过期；null=无固定期限或无结束日期 */
    private Long expireDays;
    /** RED 7 天内 / YELLOW 30 天内 / 空 */
    private String expireFlag;
    private LocalDateTime createdAt;
}

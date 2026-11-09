package com.laimengchao.sso.ehr.module.contract.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 员工合同：起止日期、类型、状态与续签来源。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_emp_contract")
public class Contract extends BaseEntity {

    private Long employeeId;
    private String contractNo;
    /** FIXED 固定期限 / UNFIXED 无固定期限 / INTERN 实习 / LABOR 劳务 */
    private String contractType;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate signDate;
    /** ACTIVE / PENDING / TERMINATING / EXPIRED / RENEWED / TERMINATED */
    private String status;
    /** 续签来源合同ID：PENDING 行指向被续签的旧合同 */
    private Long renewFromId;
    private Long fileId;
    private String remark;
}

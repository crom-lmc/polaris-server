package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 员工银行卡。一张档案可录多张卡，is_primary=1 的是工资卡且每张档案最多一张。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_emp_bank")
public class EmployeeBank extends BaseEntity {

    private Long employeeId;
    /** 银行（字典 ehr.bank 的 value，如 icbc） */
    private String bank;
    /** 卡号（借记/贷记，存明文——内网演示系统；生产建议加密） */
    private String accountNo;
    private String holderName;
    /** 开户行/支行 */
    private String branch;
    /** 1=工资卡（每档案至多一张） */
    private Integer isPrimary;
    private String remark;
}

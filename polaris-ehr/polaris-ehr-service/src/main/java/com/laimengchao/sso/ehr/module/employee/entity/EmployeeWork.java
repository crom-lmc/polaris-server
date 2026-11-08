package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 工作经历（入职本公司前的一段工作） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_emp_work")
public class EmployeeWork extends BaseEntity {

    private Long employeeId;
    private String company;
    private String position;
    private LocalDate startDate;
    /** 结束日期；null = 至今（中断工作经历可填） */
    private LocalDate endDate;
    /** 离职原因 */
    private String leaveReason;
    private String remark;
}

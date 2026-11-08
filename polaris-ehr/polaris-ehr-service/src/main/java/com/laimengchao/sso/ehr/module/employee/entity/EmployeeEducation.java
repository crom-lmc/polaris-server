package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 教育背景（一条一段学历经历） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_emp_education")
public class EmployeeEducation extends BaseEntity {

    private Long employeeId;
    private String school;
    private String major;
    /** 学历（字典 ehr.education，如 bachelor） */
    private String degree;
    private LocalDate startDate;
    /** 结束日期；null = 至今 */
    private LocalDate endDate;
    private String remark;
}

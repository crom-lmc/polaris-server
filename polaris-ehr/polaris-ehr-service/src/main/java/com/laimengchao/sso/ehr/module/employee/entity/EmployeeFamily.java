package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 家庭成员 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_emp_family")
public class EmployeeFamily extends BaseEntity {

    private Long employeeId;
    /** 关系（字典 ehr.family_relation，如 father） */
    private String relation;
    private String name;
    /** 工作单位 */
    private String company;
    private String phone;
    private String remark;
}

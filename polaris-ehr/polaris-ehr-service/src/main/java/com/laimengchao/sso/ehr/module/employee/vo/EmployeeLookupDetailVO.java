package com.laimengchao.sso.ehr.module.employee.vo;

import lombok.Data;

/**
 * 员工快捷查询详情（控制台弹窗纵向展示）。
 * <p>
 * 只含非隐私信息；性别已翻译成中文，部门为组织全路径。
 */
@Data
public class EmployeeLookupDetailVO {

    private String name;
    private String empNo;
    private String account;
    private String gender;
    private String position;
    private String department;
    private String email;
}

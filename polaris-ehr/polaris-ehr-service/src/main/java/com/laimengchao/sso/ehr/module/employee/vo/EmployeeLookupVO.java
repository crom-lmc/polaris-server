package com.laimengchao.sso.ehr.module.employee.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 员工快捷查询结果项（控制台使用）。
 * <p>
 * 刻意只给「找人」需要的三个字段：姓名、职位、邮件。手机号/身份证/地址等隐私字段一律不返回，
 * 详情也仅限姓名/工号/账号/性别/职位/部门/邮件这几项。
 */
@Data
public class EmployeeLookupVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String name;
    private String position;
    private String email;
}

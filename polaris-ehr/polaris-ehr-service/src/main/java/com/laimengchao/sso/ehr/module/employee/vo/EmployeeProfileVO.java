package com.laimengchao.sso.ehr.module.employee.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 人员档案聚合视图：主档案 + 银行卡 / 教育背景 / 工作经历 / 家庭成员，
 * 一次查询给「查看」抽屉用，省得前端发 5 个请求。
 */
@Data
public class EmployeeProfileVO {

    private EmployeeVO employee;

    private List<BankVO> banks;
    private List<EducationVO> educations;
    private List<WorkVO> works;
    private List<FamilyVO> families;

    @Data
    public static class BankVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long employeeId;
        private String bank;
        private String bankName;
        private String accountNo;
        private String holderName;
        private String branch;
        private Integer isPrimary;
        private String remark;
    }

    @Data
    public static class EducationVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long employeeId;
        private String school;
        private String major;
        private String degree;
        private String degreeName;
        private LocalDate startDate;
        private LocalDate endDate;
        private String remark;
    }

    @Data
    public static class WorkVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long employeeId;
        private String company;
        private String position;
        private LocalDate startDate;
        private LocalDate endDate;
        private String leaveReason;
        private String remark;
    }

    @Data
    public static class FamilyVO {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @JsonSerialize(using = ToStringSerializer.class)
        private Long employeeId;
        private String relation;
        private String relationName;
        private String name;
        private String company;
        private String phone;
        private String remark;
    }
}

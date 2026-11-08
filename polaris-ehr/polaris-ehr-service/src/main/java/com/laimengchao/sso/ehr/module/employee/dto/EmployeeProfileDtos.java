package com.laimengchao.sso.ehr.module.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人员子档案（银行卡/教育/工作/家庭成员）新增与编辑入参。
 * 格式类校验用 Bean Validation 声明，跨字段规则（日期区间、工资卡唯一）在服务里做。
 */
public final class EmployeeProfileDtos {

    private EmployeeProfileDtos() {
    }

    @Data
    public static class BankRequest {
        @NotBlank(message = "银行必选")
        private String bank;
        @NotBlank(message = "卡号必填")
        @Pattern(regexp = "^[0-9]{8,32}$", message = "卡号应为 8-32 位数字")
        private String accountNo;
        @NotBlank(message = "持卡人必填")
        @Size(max = 64, message = "持卡人过长")
        private String holderName;
        @Size(max = 128, message = "开户行过长")
        private String branch;
        /** 1=工资卡 */
        private Integer isPrimary;
        @Size(max = 255, message = "备注过长")
        private String remark;
    }

    @Data
    public static class EducationRequest {
        @NotBlank(message = "学校必填")
        @Size(max = 128, message = "学校名过长")
        private String school;
        @Size(max = 128, message = "专业过长")
        private String major;
        @NotBlank(message = "学历必选")
        private String degree;
        @NotBlank(message = "开始日期必填")
        private String startDate;
        /** 结束日期；空 = 至今 */
        private String endDate;
        @Size(max = 255, message = "备注过长")
        private String remark;
    }

    @Data
    public static class WorkRequest {
        @NotBlank(message = "工作单位必填")
        @Size(max = 128, message = "单位名过长")
        private String company;
        @Size(max = 64, message = "职位过长")
        private String position;
        @NotBlank(message = "开始日期必填")
        private String startDate;
        private String endDate;
        @Size(max = 255, message = "离职原因过长")
        private String leaveReason;
        @Size(max = 255, message = "备注过长")
        private String remark;
    }

    @Data
    public static class FamilyRequest {
        @NotBlank(message = "关系必选")
        private String relation;
        @NotBlank(message = "姓名必填")
        @Size(max = 64, message = "姓名过长")
        private String name;
        @Size(max = 128, message = "工作单位过长")
        private String company;
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "联系电话格式不正确")
        private String phone;
        @Size(max = 255, message = "备注过长")
        private String remark;
    }
}

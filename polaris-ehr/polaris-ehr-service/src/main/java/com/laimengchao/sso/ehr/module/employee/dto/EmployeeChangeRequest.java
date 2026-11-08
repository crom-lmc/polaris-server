package com.laimengchao.sso.ehr.module.employee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 人员信息变更提交入参（试用期/在职员工的「变更」）。
 * <p>
 * 基本信息只收变更表单里可编辑的 7 项；四类子表传「目标态整表」：
 * 带 id 的是已有行（内容可能改了）、不带 id 的是新增行、库里有但没提交上来的算删除，
 * 由服务端与现状逐行比对出 ADD/DEL/MOD，连同基本信息差异一起进变更单与审批快照。
 */
@Data
public class EmployeeChangeRequest {

    private String mobile;
    private String maritalStatus;
    private String politicalStatus;
    private String education;
    private String emergencyContact;
    private String emergencyPhone;
    private String currentAddress;

    /** 银行卡目标态；null 表示本次不涉及该子表（跳过比对） */
    @Valid
    private List<BankRow> banks;
    /** 教育背景目标态 */
    @Valid
    private List<EducationRow> educations;
    /** 工作经历目标态 */
    @Valid
    private List<WorkRow> works;
    /** 家庭成员目标态 */
    @Valid
    private List<FamilyRow> families;

    @Data
    public static class BankRow {
        /** 已有行主键；新增行为空 */
        private Long id;
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
    public static class EducationRow {
        private Long id;
        @NotBlank(message = "学校必填")
        @Size(max = 128, message = "学校名过长")
        private String school;
        @Size(max = 128, message = "专业过长")
        private String major;
        @NotBlank(message = "学历必选")
        private String degree;
        @NotBlank(message = "开始日期必填")
        private String startDate;
        private String endDate;
        @Size(max = 255, message = "备注过长")
        private String remark;
    }

    @Data
    public static class WorkRow {
        private Long id;
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
    public static class FamilyRow {
        private Long id;
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

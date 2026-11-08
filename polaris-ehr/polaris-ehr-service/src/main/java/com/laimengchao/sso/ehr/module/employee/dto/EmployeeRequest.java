package com.laimengchao.sso.ehr.module.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 员工新增/编辑入参。openAccount=false 时只建档不开户（入职流程未走完等场景） */
@Data
public class EmployeeRequest {

    /**
     * 工号已废弃手动填写：前端禁用、后端一律忽略此字段，统一由系统自动生成。
     * 字段保留只为老客户端兼容（传了也不生效）。
     */
    private String empNo;

    @NotBlank(message = "姓名必填")
    private String name;

    private Integer gender;
    private String mobile;
    private String email;
    private String nativePlace;
    /** 籍贯-省（身份证地址码自动带出，可手动改） */
    private String nativeProvince;
    /** 籍贯-市（身份证地址码自动带出，禁改） */
    private String nativeCity;
    /** 籍贯-区/县（身份证地址码自动带出，禁改） */
    private String nativeDistrict;
    /** 籍贯-详细地址 */
    private String nativeDetail;
    private String idCard;
    /** yyyy-MM-dd；为空且身份证合法时由身份证推导 */
    private String birthday;
    private String nation;
    private String maritalStatus;
    private String politicalStatus;
    private String education;
    private String emergencyContact;
    private String emergencyPhone;
    private String currentAddress;

    @NotNull(message = "所属组织必填")
    private Long orgId;

    private String position;
    /** yyyy-MM-dd */
    private String hireDate;
    /**
     * 试用期时长（月）：1-12。免试用期（exemptProbation=true）时无需传。
     * 提交入职前后端会校验必填且合法。
     */
    private Integer probationMonths;
    /** 是否免试用期：true 时入职审批通过后直接转正式（40），无需走试用期 */
    private Boolean exemptProbation;
    private Integer status;
    private String remark;
    /** 是否同时开通统一账号，默认 true */
    private Boolean openAccount;
    /** 初始密码（留空则服务端随机生成，仅在响应里返回一次） */
    private String password;
}

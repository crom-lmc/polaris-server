package com.laimengchao.sso.ehr.module.employee.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 员工视图：补上组织全路径，前端列表不用再自己拼祖先链 */
@Data
public class EmployeeVO {

    /** 19 位雪花 ID 一律转字符串，避免 JS 精度丢失后回传 404 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String empNo;
    private String name;
    private Integer gender;
    private String mobile;
    private String email;
    private String nativePlace;
    /** 籍贯-省/市/区县/详细地址（结构化，前端编辑回填用） */
    private String nativeProvince;
    private String nativeCity;
    private String nativeDistrict;
    private String nativeDetail;
    private String idCard;
    private LocalDate birthday;
    /** 周岁，由出生日期动态计算，不落库 */
    private Integer age;
    private String nation;
    /** 字典项显示名（前端免二次查字典） */
    private String nationName;
    private String maritalStatus;
    private String maritalStatusName;
    private String politicalStatus;
    private String politicalStatusName;
    private String education;
    private String educationName;
    private String emergencyContact;
    private String emergencyPhone;
    private String currentAddress;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
    /** 所属节点名 */
    private String orgName;
    /** 组织全路径，如「集团 / 子公司 / 研发部」 */
    private String orgPathName;
    private String position;
    private LocalDate hireDate;
    /** 试用期时长（月）：1-12；免试用期为 null */
    private Integer probationMonths;
    /** 是否免试用期：true 时入职审批通过后直接转正式 */
    private Boolean exemptProbation;
    /** 预计转正时间 = 入职日期 + 试用期月数；免试用期/缺失为 null */
    private LocalDate expectedRegularizeDate;
    private Integer status;
    /** 状态中文：待入职 / 入职审批中 / 试用期 / 在职 / 离职中 / 已离职 */
    private String statusName;
    /** 入职审批进度：null 未开始 / 0 待部门审批 / 1 待人事审批 / 2 已通过 / -1 已驳回 */
    private Integer onboardStep;
    /** 入职审批记录（JSON 数组） */
    private String onboardApprovals;
    /**
     * 入职审批单 ID（审批中心 settings 的 appr_instance.id）。
     * <p>
     * 雪花 ID 必须转字符串：19 位数字在浏览器里会丢精度，前端拿去做「查看审批详情」的入参会查到别的单。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long onboardInstanceId;
    /** 转正状态：0 未申请 / 1 待部门审批 / 2 待人事审批 / 3 已通过 / -1 已驳回 */
    private Integer regularizeStatus;
    /** 转正审批记录（JSON 数组） */
    private String regularizeApprovals;
    private LocalDate resignDate;
    private String resignReason;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String accountUsername;
    private String remark;
    private String createdAt;
    /** 离职/复职时是否实际变更了统一账号状态（仅这两个接口的响应里有值） */
    private Boolean accountDisabled;

    /**
     * 本次保存是否走了审批（true=已提交信息变更审批、尚未生效）。
     * 只在 update 的响应里可能出现；前端据此把提示从「已保存」换成「已提交审批」。
     */
    private Boolean pendingApproval;

    /* ---------- 当前在途审批（统一走审批中心后新增） ---------- */

    /** 当前在途审批单 ID；有值即表示「XX审批中」，前端据此点开审批详情 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;
    /** ehr.onboard / ehr.regularize / ehr.emp_update / ehr.resign */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String approvalBizType;

    /** 本次开户的账号信息（只在创建/补开户的响应里出现，初始密码仅此一次） */
    private AccountBrief account;

    @Data
    public static class AccountBrief {
        private String username;
        private String initialPassword;
        /** true=本次新建；false=复用已有账号并补齐授权 */
        private boolean created;
        private List<String> apps;
        private List<String> roles;
    }
}

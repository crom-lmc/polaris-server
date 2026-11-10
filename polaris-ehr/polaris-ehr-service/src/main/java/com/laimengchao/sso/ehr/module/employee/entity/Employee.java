package com.laimengchao.sso.ehr.module.employee.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 员工档案。与统一账号中心是 1:1（account_id），但两边生命周期独立：
 * 员工离职只停档案，账号由账号中心管理，避免「删档案顺手删掉别人还在用的账号」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_employee")
public class Employee extends BaseEntity {

    private String empNo;
    private String name;
    /** 1 男 / 2 女 / 0 未知 */
    private Integer gender;
    private String mobile;
    private String email;
    /** 籍贯（省市区拼接文本，如 陕西省西安市雁塔区；由下方结构化字段生成，兼容旧展示） */
    private String nativePlace;
    /** 籍贯-省（如 陕西省；身份证地址码自动带出，可手动改） */
    private String nativeProvince;
    /** 籍贯-市（如 西安市；身份证地址码自动带出，禁改） */
    private String nativeCity;
    /** 籍贯-区/县（如 雁塔区；身份证地址码自动带出，禁改） */
    private String nativeDistrict;
    /** 籍贯-详细地址（乡镇/街道/门牌） */
    private String nativeDetail;
    /** 18 位身份证号；出生日期/性别为空时由它推导 */
    private String idCard;
    private LocalDate birthday;
    /** 民族（字典 general.nation 的 value，如 han） */
    private String nation;
    /** 婚姻状况（字典 ehr.marital_status） */
    private String maritalStatus;
    /** 政治面貌（字典 ehr.political_status） */
    private String politicalStatus;
    /** 最高学历（字典 ehr.education） */
    private String education;
    private String emergencyContact;
    private String emergencyPhone;
    private String currentAddress;
    /** 所属组织节点：集团/子公司/部门/项目/子项目都可以直接挂人 */
    private Long orgId;
    private String position;
    private LocalDate hireDate;
    /** 生命周期状态：10 待入职 / 20 入职审批中 / 25 入职审批通过 / 30 试用期 / 40 在职 / 50 离职中 / 60 已离职 */
    private Integer status;
    /**
     * 试用期时长（月）：1-12。免试用期时为 null。仅 status=10/20/25 期间可维护，
     * 一旦进入试用期/正式即锁定，作为转正预期的依据。
     */
    private Integer probationMonths;
    /** 是否免试用期：true 时入职审批通过后直接转正式（40），无需走试用期 */
    private Boolean exemptProbation;
    /** 预计转正时间 = 入职日期 + 试用期时长（月）；免试用期或时长缺失时为 null。由服务端推导，不落库以外的来源 */
    private LocalDate expectedRegularizeDate;
    /**
     * 入职审批进度：null 未开始 / 0 待部门审批 / 1 待人事审批 / 2 已通过（入职审批通过） / -1 已驳回。
     * 仅 status=20/25 时有意义。
     */
    private Integer onboardStep;
    /** 入职审批记录（JSON 数组，见 ApprovalRecord）；status 回到 10 时不清空，保留痕迹 */
    private String onboardApprovals;
    /**
     * 转正状态：0 未申请 / 1 待部门审批 / 2 待人事审批 / 3 已通过（转正） / -1 已驳回。
     * 仅 status=30（试用期）期间可被员工自助申请。
     */
    private Integer regularizeStatus;
    /** 转正审批记录（JSON 数组） */
    private String regularizeApprovals;

    /** 离职日期：status=60 时记录，复职后清空 */
    private LocalDate resignDate;
    /** 离职原因 */
    private String resignReason;
    /** 统一账号中心账户 ID；null 表示尚未开户 */
    private Long accountId;
    private String accountUsername;
    private String remark;
    /** 来源 MANUAL / RECRUIT / PORTAL / IMPORT */
    private String source;
    /** 来源 Offer ID（招聘转入时写入，唯一） */
    private Long sourceOfferId;
    /** 司龄起算日（默认入职日，HR 核定后维护） */
    private LocalDate seniorityStartDate;
    /** 当前司龄（整年，每日任务刷新） */
    private java.math.BigDecimal seniorityYears;
    /** 首次参加工作时间（HR 核定，累计工龄用于法定年假档位） */
    private LocalDate firstWorkDate;
    /** 最近一次司龄确认时间 */
    private java.time.LocalDateTime seniorityConfirmedAt;
    /** 最近一次司龄确认人 */
    private Long seniorityConfirmedBy;
}

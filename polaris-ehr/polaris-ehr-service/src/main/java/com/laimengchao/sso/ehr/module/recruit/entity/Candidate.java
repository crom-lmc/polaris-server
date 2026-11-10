package com.laimengchao.sso.ehr.module.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 招聘候选人：按阶段分列展示，淘汰后不可再发起 Offer */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_candidate")
public class Candidate extends BaseEntity {

    /** 候选人编号：由配置中心单号规则生成 */
    private String candidateNo;
    private String name;
    private String mobile;
    private String email;
    private String source;
    private String position;
    private Long orgId;
    /** RESUME / INTERVIEW / OFFER / REJECTED */
    private String stage;
    private Long ownerId;
    private String ownerName;
    private String remark;

    /** 关联招聘职位（门户投递） */
    private Long jobId;
    /** 0未知/1男/2女 */
    private Integer gender;
    private LocalDate birthday;
    private String education;
    private String school;
    private String major;
    private BigDecimal workYears;
    private String currentCompany;
    private BigDecimal expectedSalary;
    /** 证件号：仅 HR 录入，列表输出掩码 */
    private String idCard;
    /** 主简历附件ID（biz_type=CANDIDATE） */
    private Long resumeFileId;
    /** 建档后回写员工ID */
    private Long employeeId;
    private String trackCode;
    /** 投递幂等键 jobId:mobileHash */
    private String applyKey;
    private LocalDateTime appliedAt;
    private LocalDateTime lastStageAt;
    private LocalDateTime consentAt;
    private String consentVersion;
    private LocalDate retainUntil;
}

package com.laimengchao.sso.ehr.module.recruit.vo;

import lombok.Data;

/** 候选人列表项；ID 以字符串下发，避免前端 JS 精度丢失 */
@Data
public class CandidateVO {

    private String id;
    private String name;
    private String mobile;
    private String email;
    private String source;
    private String position;
    private String orgId;
    private String orgPath;
    private String stage;
    private String ownerId;
    private String ownerName;
    private String remark;
    /** 当前是否有审批中/待确认/已确认的 Offer */
    private Boolean hasActiveOffer;
    private String offerId;
    private String offerStatus;
    private String createdAt;

    private String jobId;
    private String jobTitle;
    private Integer gender;
    private String birthday;
    private String education;
    private String school;
    private String major;
    private String workYears;
    private String currentCompany;
    private String expectedSalary;
    /** 证件号：列表/详情默认掩码，详情编辑按 recruit:manage 返回明文 */
    private String idCard;
    private String resumeFileId;
    private String resumeFileName;
    private String employeeId;
    private String employeeStatus;
    private String employeeStatusLabel;
    private Boolean accountOpened;
    private String appliedAt;
    private String lastStageAt;
    /** 同手机号在其他职位的在途记录数（只提示不合并） */
    private Integer activeApplyCount;

    /** 已判定通过的最大面试轮次；null 表示尚未通过任何一轮 */
    private Integer interviewRoundNo;
    /** 已约但尚未反馈的面试场次 */
    private Integer interviewPendingCount;
    /** 最近一场待反馈面试时间 */
    private String interviewNextAt;
    /** 最近一条沟通记录时间与摘要 */
    private String lastCommAt;
    private String lastCommContent;
}

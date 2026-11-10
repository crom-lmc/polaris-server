package com.laimengchao.sso.ehr.module.employee.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/** 招聘录用转入建档入参：候选人 / Offer 信息一次性带出，减少 HR 重新录入 */
@Data
@Builder
public class PendingArchiveCommand {

    private String name;
    private String mobile;
    private String email;
    private Integer gender;
    private LocalDate birthday;
    private String idCard;
    private String education;
    private Long orgId;
    private String position;
    private LocalDate hireDate;
    private String remark;
    /** 来源 MANUAL / RECRUIT / PORTAL / IMPORT */
    private String source;
    /** 来源 Offer ID（唯一，幂等兜底） */
    private Long sourceOfferId;
    /** 候选人主简历附件ID，建档后复制到员工附件 */
    private Long resumeFileId;
}

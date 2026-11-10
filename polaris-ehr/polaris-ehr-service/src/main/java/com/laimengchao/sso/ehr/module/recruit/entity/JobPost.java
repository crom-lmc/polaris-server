package com.laimengchao.sso.ehr.module.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 对外招聘职位：门户只展示 PUBLISHED 且未截止的记录 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_job_post")
public class JobPost extends BaseEntity {

    private String jobNo;
    private String title;
    private Long orgId;
    /** FULL_TIME / INTERN / PART_TIME */
    private String jobType;
    private String workCity;
    private Integer headcount;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String educationReq;
    private String experienceReq;
    private String description;
    private String requirement;
    /** 英文逗号分隔 */
    private String tags;
    /** DRAFT / PUBLISHED / CLOSED */
    private String status;
    private LocalDateTime publishAt;
    private LocalDate closeAt;
    private Integer sortNo;
    private Long ownerId;
    private Long createdBy;
    /** 投递数（列表聚合，不落库） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Long applyCount;
}

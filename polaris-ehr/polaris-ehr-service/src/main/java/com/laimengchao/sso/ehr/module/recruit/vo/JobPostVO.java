package com.laimengchao.sso.ehr.module.recruit.vo;

import lombok.Data;

/** 招聘职位列表/详情出参；公开接口只下发展示字段 */
@Data
public class JobPostVO {

    private String id;
    private String jobNo;
    private String title;
    private String orgId;
    private String orgPath;
    private String jobType;
    private String workCity;
    private Integer headcount;
    private String salaryMin;
    private String salaryMax;
    private String salaryRange;
    private String educationReq;
    private String experienceReq;
    private String description;
    private String requirement;
    private String tags;
    private String status;
    private String publishAt;
    private String closeAt;
    private Integer sortNo;
    private String ownerId;
    private Long applyCount;
    private String createdAt;
}

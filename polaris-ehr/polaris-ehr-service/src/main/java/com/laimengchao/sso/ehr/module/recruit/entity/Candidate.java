package com.laimengchao.sso.ehr.module.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 招聘候选人：按阶段分列展示，淘汰后不可再发起 Offer */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_candidate")
public class Candidate extends BaseEntity {

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
}

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
    /** 当前是否有审批中/已通过的 Offer */
    private Boolean hasActiveOffer;
    private String offerId;
    private String offerStatus;
    private String createdAt;
}

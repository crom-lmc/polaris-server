package com.laimengchao.sso.ehr.module.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** HR 与候选人的沟通留痕（薪资沟通、电话沟通等），仅内部可见 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_candidate_comm")
public class CandidateComm extends BaseEntity {

    /** 沟通记录单号：由配置中心单号规则生成 */
    private String commNo;
    private Long candidateId;
    /** OFFER_TALK 薪资沟通 / PHONE 电话 / OTHER 其他 */
    private String type;
    private String content;
    private BigDecimal expectedSalary;
    private LocalDateTime occurredAt;
    private Long operatorId;
    private String operatorName;
}

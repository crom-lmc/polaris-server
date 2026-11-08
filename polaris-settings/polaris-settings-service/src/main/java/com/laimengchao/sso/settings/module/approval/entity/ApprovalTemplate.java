package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批模板。一个业务场景对应一个启用中的模板。
 * <p>
 * version 每次保存 +1；实例发起时会把步骤/分支结构快照进 appr_instance.snapshot，
 * 因此改模板不影响在途单——这是流程与配置解耦的关键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cfg_approval_template")
public class ApprovalTemplate extends BaseEntity {

    /** 模板编码，如 ehr_onboard_default */
    private String code;
    private String name;
    /** 归属应用 ehr / cms / blog */
    private String appCode;
    /** 业务场景，如 ehr.onboard */
    private String bizType;
    private String bizName;
    /** 1 启用 / 0 停用；停用后发起会退化为内置默认流程 */
    private Integer enabled;
    private Integer version;
    private String remark;
}

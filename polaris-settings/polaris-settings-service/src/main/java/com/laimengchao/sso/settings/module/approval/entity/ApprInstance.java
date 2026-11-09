package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批实例（在途单据）。业务模块不再保存审批过程，全部落在这里。
 * <p>
 * 三份快照是核心：
 * <ul>
 *   <li>formData —— 业务表单在提交那一刻的冻结值。员工后来调了部门，审批人看到的仍是旧部门</li>
 *   <li>snapshot —— 流程结构快照。改模板不影响在途单</li>
 *   <li>tableBindings —— 数据表绑定快照。改场景绑定的表不影响在途单</li>
 * </ul>
 * bizId 用 VARCHAR 而不是 BIGINT：不同业务的单据主键可能是数字也可能是字符串。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appr_instance")
public class ApprInstance extends BaseEntity {

    private Long templateId;
    private String templateCode;
    private Integer templateVersion;
    private String bizType;
    private String bizName;
    /** 本次业务单据 ID：可能是主体 ID（SUBJECT），也可能是申请单/记录行 ID（DOC） */
    private String bizId;
    /**
     * 业务主体 ID（员工 / 组织 / 文章）。与 bizId 显式解耦：
     * 列表按 (bizType, subjectId) 反查在途单，回调按它定位业务主体，不再猜 bizId 语义。
     */
    private String subjectId;
    /**
     * bizId 的语义：SUBJECT=业务主体 ID / DOC=本次单据 ID。
     * NULL 表示补列前的存量单，运行期按 formData 兼容推断。
     */
    private String bizIdKind;
    /** 业务方自己的业务单号，由调用方传入 */
    private String bizNo;
    /** 审批单自身的单号，按配置中心「单号规则」中 appr_instance 的规则生成 */
    private String apprNo;
    private String title;

    private Long applicantId;
    private String applicantName;
    private Long applicantOrgId;
    private String orgPath;

    /** 业务表单快照 JSON */
    private String formData;
    /** 流程快照 JSON：步骤 + 分支 + 解析后的审批人 */
    private String snapshot;
    /**
     * 数据表绑定快照 JSON（{@code SceneTableBindingsDto} 序列化）。
     * <p>
     * 发起这一刻从场景配置里冻结，之后管理端改场景绑定不影响在途单——第三份快照，
     * 与 formData（表单值）、snapshot（流程结构）同等语义。
     */
    private String tableBindings;
    /** 页面配置锁定版本，空=跟随最新 */
    private Integer pageVersion;

    /** 0 进行中 1 已通过 2 已驳回 3 已撤回 4 已终止 */
    private Integer status;
    private Integer currentStep;
    private Integer totalStep;
    /** 业务回调：0 不需要 1 待回调 2 成功 3 失败 */
    private Integer callbackStatus;
    /** 终审回调地址（业务方提供，空=不回调） */
    private String callbackUrl;
    private LocalDateTime finishedAt;
    /** 被催办次数（运营页催办按钮累加） */
    private Integer urgeCount;
    /** 最近一次催办时间 */
    private LocalDateTime lastUrgeAt;
}

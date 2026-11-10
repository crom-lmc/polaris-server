package com.laimengchao.sso.settings.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批实例视图。
 * <p>
 * steps 用于画步骤条（含未开始的），tasks 用于画每个步骤下的分支与审批人。
 * 前端按 stepSeq 分组即可，不依赖额外的树结构。
 * <p>
 * 主键一律序列化成字符串：雪花 ID 是 19 位，超过 JS 的 MAX_SAFE_INTEGER，
 * 直接给数字会在浏览器里被抹掉后几位，拿回来查库就是「审批单不存在」。
 */
@Data
public class ApprovalInstanceVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String bizType;
    private String bizName;
    private String bizId;
    /** 业务方自己的业务单号 */
    private String bizNo;
    /** 审批单自身的单号 */
    private String apprNo;
    private String title;

    /** 发起人账号 ID：前端要和登录人的 accountId 比对决定是否显示「撤回」，必须转字符串，否则 19 位精度丢失后永远不相等 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;
    private String applicantName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantOrgId;
    private String orgPath;

    private String templateCode;
    private Integer templateVersion;

    private Integer status;
    private String statusName;
    private Integer currentStep;
    private Integer totalStep;
    /** 0 不需要 / 1 待回调 / 2 成功 / 3 失败 */
    private Integer callbackStatus;

    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    /** 表单快照 */
    private Map<String, Object> formData = new LinkedHashMap<>();

    private List<StepBriefVO> steps = new ArrayList<>();
    private List<ApprovalTaskVO> tasks = new ArrayList<>();

    /** 当前登录人在本单上的待办任务 ID，无则 null（前端据此决定按钮可用性） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long myTaskId;

    /* ---- 当前登录人在本单已完成的审批结果（「已审批」视图用） ---- */

    /** 任务状态：1 已通过 / 2 已驳回；没审过则为 null */
    private Integer myApprovalStatus;
    private String myApprovalStatusName;
    /** 我审批时所在的步骤名 */
    private String myApprovalStepName;
    private String myApprovalOpinion;
    private LocalDateTime myApprovalAt;
}

package com.laimengchao.sso.settings.module.approval.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.module.approval.constant.InstanceStatus;
import com.laimengchao.sso.settings.module.approval.constant.TaskStatus;
import com.laimengchao.sso.settings.module.approval.entity.ApprAction;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprTask;
import com.laimengchao.sso.settings.module.approval.mapper.ApprActionMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprInstanceMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprTaskMapper;
import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalInstanceVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalTaskVO;
import com.laimengchao.sso.settings.module.approval.vo.StepBriefVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审批查询：详情、待办五视图（含「已审批」）、流水。
 * <p>
 * 「待我审批」只认 (approver_id = 我, status = 待办)——审批权限由任务分配决定，不靠权限点，
 * 否则每个业务应用都得给全员配一次 settings 的角色。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalQueryService {

    private final ApprInstanceMapper instanceMapper;
    private final ApprTaskMapper taskMapper;
    private final ApprActionMapper actionMapper;
    private final ApprovalEngine engine;
    private final ObjectMapper objectMapper;

    /** 实例详情：步骤条 + 分支任务 + 表单快照 */
    public ApprovalInstanceVO detail(Long instanceId, Long viewerId) {
        ApprInstance inst = engine.require(instanceId);
        requireParticipant(inst, viewerId);
        return toVO(inst, viewerId);
    }

    /** 详情和渲染上下文共用同一参与人口径：申请人、审批/抄送人、委托人均可查看。 */
    public void requireParticipant(ApprInstance inst, Long viewerId) {
        if (viewerId != null && viewerId.equals(inst.getApplicantId())) {
            return;
        }
        Long participants = viewerId == null ? 0L : taskMapper.selectCount(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, inst.getId())
                .and(w -> w.eq(ApprTask::getApproverId, viewerId)
                        .or().eq(ApprTask::getDelegateOwnerId, viewerId)));
        if (participants == null || participants == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该审批单");
        }
    }

    /**
     * 五视图。
     *
     * @param scope pending 待我审批（含代收） / approved 已审批 / mine 我发起 /
     *              cc 抄送我 / delegated 我委托出去的
     */
    public List<ApprovalInstanceVO> list(Long accountId, String scope) {
        String s = scope == null ? "pending" : scope;
        List<ApprInstance> instances = new ArrayList<>();

        switch (s) {
            case "mine" -> instances = instanceMapper.selectList(Wrappers.<ApprInstance>lambdaQuery()
                    .eq(ApprInstance::getApplicantId, accountId)
                    .orderByDesc(ApprInstance::getCreatedAt)
                    .last("limit 100"));
            case "approved" -> {
                // 「已审批」只认我作为审批人真正点过通过/驳回的 APPROVE 任务：
                // 抄送(CC)不是审批、转办/作废/跳过也不算我审过这张单。
                // 同一个人在一张单上可能有多条任务（多步/加签），按实例去重、保持我最近处理的顺序。
                List<ApprTask> done = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                        .eq(ApprTask::getApproverId, accountId)
                        .eq(ApprTask::getTaskKind, "APPROVE")
                        .in(ApprTask::getStatus, List.of(
                                TaskStatus.APPROVED.getCode(), TaskStatus.REJECTED.getCode()))
                        .orderByDesc(ApprTask::getActedAt)
                        .last("limit 100"));
                instances = byInstanceIdsOrdered(
                        done.stream().map(ApprTask::getInstanceId).collect(Collectors.toList()));
            }
            case "cc" -> {
                List<ApprTask> cc = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                        .eq(ApprTask::getApproverId, accountId)
                        .eq(ApprTask::getTaskKind, "CC")
                        .orderByDesc(ApprTask::getCreatedAt)
                        .last("limit 100"));
                instances = byInstanceIds(cc.stream().map(ApprTask::getInstanceId).collect(Collectors.toList()));
            }
            case "delegated" -> {
                List<ApprTask> ds = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                        .eq(ApprTask::getDelegateOwnerId, accountId)
                        .orderByDesc(ApprTask::getCreatedAt)
                        .last("limit 100"));
                instances = byInstanceIds(ds.stream().map(ApprTask::getInstanceId).collect(Collectors.toList()));
            }
            default -> {
                List<ApprTask> mine = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                        .eq(ApprTask::getApproverId, accountId)
                        .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode())
                        .eq(ApprTask::getTaskKind, "APPROVE")
                        .orderByAsc(ApprTask::getCreatedAt)
                        .last("limit 100"));
                instances = byInstanceIds(mine.stream().map(ApprTask::getInstanceId).collect(Collectors.toList()));
            }
        }
        // 已撤回的实例只留档，不进入任何视图列表（待办 / 已办 / 我发起 / 抄送 / 委托）
        // 不能直接 removeIf：部分分支返回的是 List.of() 等不可变集合，会抛 UnsupportedOperationException
        instances = instances.stream()
                .filter(i -> !InstanceStatus.WITHDRAWN.getCode().equals(i.getStatus()))
                .collect(Collectors.toList());
        List<ApprovalInstanceVO> list = new ArrayList<>();
        for (ApprInstance inst : instances) {
            list.add(toVO(inst, accountId));
        }
        // 待办按「谁在等我」排序：越早提交越靠前，避免老单被埋
        if ("pending".equals(s)) {
            list.sort(Comparator.comparing(ApprovalInstanceVO::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return list;
    }

    private List<ApprInstance> byInstanceIds(List<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        if (distinct.isEmpty()) {
            return List.of();
        }
        List<ApprInstance> list = instanceMapper.selectBatchIds(distinct);
        list.sort(Comparator.comparing(ApprInstance::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return list;
    }

    /** 与 {@link #byInstanceIds} 一样按实例去重，但保持传入的任务顺序（用于「最近我审过的」） */
    private List<ApprInstance> byInstanceIdsOrdered(List<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        if (distinct.isEmpty()) {
            return List.of();
        }
        Map<Long, ApprInstance> byId = instanceMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(ApprInstance::getId, x -> x, (a, b) -> a));
        List<ApprInstance> out = new ArrayList<>();
        for (Long id : distinct) {
            ApprInstance inst = byId.get(id);
            if (inst != null) {
                out.add(inst);
            }
        }
        return out;
    }

    public List<ApprAction> actions(Long instanceId) {
        return actionMapper.selectList(Wrappers.<ApprAction>lambdaQuery()
                .eq(ApprAction::getInstanceId, instanceId)
                .orderByAsc(ApprAction::getCreatedAt));
    }

    // ================================================================ 转换

    public ApprovalInstanceVO toVO(ApprInstance inst, Long viewerId) {
        ApprovalInstanceVO vo = new ApprovalInstanceVO();
        vo.setId(inst.getId());
        vo.setBizType(inst.getBizType());
        vo.setBizName(inst.getBizName());
        vo.setBizId(inst.getBizId());
        vo.setBizNo(inst.getBizNo());
        vo.setApprNo(inst.getApprNo());
        vo.setTitle(inst.getTitle());
        vo.setApplicantId(inst.getApplicantId());
        vo.setApplicantName(inst.getApplicantName());
        vo.setApplicantOrgId(inst.getApplicantOrgId());
        vo.setOrgPath(inst.getOrgPath());
        vo.setTemplateCode(inst.getTemplateCode());
        vo.setTemplateVersion(inst.getTemplateVersion());
        vo.setStatus(inst.getStatus());
        InstanceStatus st = InstanceStatus.of(inst.getStatus());
        vo.setStatusName(st == null ? "" : st.getLabel());
        vo.setCurrentStep(inst.getCurrentStep());
        vo.setTotalStep(inst.getTotalStep());
        vo.setCallbackStatus(inst.getCallbackStatus());
        vo.setCreatedAt(inst.getCreatedAt());
        vo.setFinishedAt(inst.getFinishedAt());
        vo.setFormData(readFormData(inst));

        List<ApprTask> tasks = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, inst.getId())
                .orderByAsc(ApprTask::getStepSeq)
                .orderByAsc(ApprTask::getNodeSeq)
                .orderByAsc(ApprTask::getSeqInNode));

        List<ApprovalTaskVO> taskVOs = new ArrayList<>();
        ApprTask myDone = null;
        for (ApprTask t : tasks) {
            taskVOs.add(toTaskVO(t));
            boolean isMine = viewerId != null && viewerId.equals(t.getApproverId())
                    && "APPROVE".equals(t.getTaskKind());
            if (isMine && TaskStatus.PENDING.getCode().equals(t.getStatus())) {
                vo.setMyTaskId(t.getId());
            }
            // 同一张单上我可能审过多步/被加签，取最近一次通过或驳回作为「我的审批结果」
            if (isMine && (TaskStatus.APPROVED.getCode().equals(t.getStatus())
                    || TaskStatus.REJECTED.getCode().equals(t.getStatus()))
                    && (myDone == null || actedLater(t, myDone))) {
                myDone = t;
            }
        }
        if (myDone != null) {
            TaskStatus ts = TaskStatus.of(myDone.getStatus());
            vo.setMyApprovalStatus(myDone.getStatus());
            vo.setMyApprovalStatusName(ts == null ? "" : ts.getLabel());
            vo.setMyApprovalStepName(myDone.getStepName());
            vo.setMyApprovalOpinion(myDone.getOpinion());
            vo.setMyApprovalAt(myDone.getActedAt());
        }
        vo.setTasks(taskVOs);
        vo.setSteps(buildSteps(inst, tasks));
        return vo;
    }

    /**
     * v5：步骤条带 branchPath / depth / parentNodeId，前端据此画树。
     * 状态不再用「全局 seq > currentStep」判断——分支子链的 seq 夹在主干中间，
     * 那样会让还没轮到的主干节点误判成 TODO，也会让已完成的分支节点误判成 CURRENT。
     * 改为：先看本步骤有没有待办，再看同路径里是否已经走到过这一步。
     */
    private List<StepBriefVO> buildSteps(ApprInstance inst, List<ApprTask> tasks) {
        List<StepBriefVO> steps = new ArrayList<>();
        ApprovalSnapshot snap;
        try {
            snap = objectMapper.readValue(inst.getSnapshot(), ApprovalSnapshot.class);
        } catch (Exception e) {
            log.warn("快照解析失败：instance={}", inst.getId());
            return steps;
        }
        boolean finished = !InstanceStatus.RUNNING.getCode().equals(inst.getStatus());
        boolean approved = InstanceStatus.APPROVED.getCode().equals(inst.getStatus());

        for (ApprovalSnapshot.Step s : snap.getSteps()) {
            String path = s.path();
            List<ApprTask> mine = tasks.stream()
                    .filter(t -> s.getSeq() != null && s.getSeq().equals(t.getStepSeq()))
                    .filter(t -> "APPROVE".equals(t.getTaskKind()))
                    .collect(Collectors.toList());
            boolean anyPending = mine.stream().anyMatch(t -> TaskStatus.PENDING.getCode().equals(t.getStatus()));
            // 同路径里出现过的最大步骤号：用于区分「条件跳过了」与「还没走到」
            int maxSeqInPath = tasks.stream()
                    .filter(t -> path.equals(t.getBranchPath() == null ? "" : t.getBranchPath()))
                    .filter(t -> "APPROVE".equals(t.getTaskKind()))
                    .mapToInt(t -> t.getStepSeq() == null ? 0 : t.getStepSeq())
                    .max().orElse(-1);
            List<ApprTask> pathTasks = tasks.stream()
                    .filter(t -> path.equals(t.getBranchPath() == null ? "" : t.getBranchPath()))
                    .filter(t -> "APPROVE".equals(t.getTaskKind()))
                    .collect(Collectors.toList());
            boolean pathPending = pathTasks.stream()
                    .anyMatch(t -> TaskStatus.PENDING.getCode().equals(t.getStatus()));

            StepBriefVO brief = new StepBriefVO();
            brief.setSeq(s.getSeq());
            brief.setName(s.getName());
            brief.setConditionDesc(nullToEmpty(s.getConditionExpr()));
            brief.setBranchPath(path);
            brief.setDepth(s.depth());
            brief.setParentNodeId(ApprovalSnapshot.lastNodeId(path) == null
                    ? null : String.valueOf(ApprovalSnapshot.lastNodeId(path)));
            brief.setIsGateway(s.isGateway());
            brief.setBranchName(branchName(snap, path));
            brief.setWaitingJoin(!path.isEmpty() && !pathTasks.isEmpty() && !pathPending && !finished);

            if (anyPending && !finished) {
                brief.setState("CURRENT");
            } else if (!mine.isEmpty()) {
                brief.setState("DONE");
            } else if (finished && approved) {
                brief.setState("DONE");
            } else if (maxSeqInPath >= (s.getSeq() == null ? Integer.MAX_VALUE : s.getSeq())) {
                brief.setState("SKIPPED");   // 走到过却没派单：条件不满足 / 无人可审
            } else {
                brief.setState("TODO");
            }
            steps.add(brief);
        }
        return steps;
    }

    /** 分支展示名：取该路径最后一段 nodeId 对应的节点名（条件描述优先） */
    private String branchName(ApprovalSnapshot snap, String path) {
        Long nodeId = ApprovalSnapshot.lastNodeId(path);
        if (nodeId == null) {
            return "";
        }
        for (ApprovalSnapshot.Step s : snap.getSteps()) {
            for (ApprovalSnapshot.Node n : s.getNodes()) {
                if (nodeId.equals(n.getNodeId())) {
                    String cond = nullToEmpty(n.getConditionExpr());
                    return cond.isEmpty() ? nullToEmpty(n.getName()) : nullToEmpty(n.getName()) + "（" + cond + "）";
                }
            }
        }
        return "";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** actedAt 为空视为更早；历史数据可能没有处理时间 */
    private static boolean actedLater(ApprTask a, ApprTask b) {
        if (a.getActedAt() == null) {
            return false;
        }
        return b.getActedAt() == null || a.getActedAt().isAfter(b.getActedAt());
    }

    public ApprovalTaskVO toTaskVO(ApprTask t) {
        ApprovalTaskVO vo = new ApprovalTaskVO();
        vo.setId(t.getId());
        vo.setInstanceId(t.getInstanceId());
        vo.setStepSeq(t.getStepSeq());
        vo.setStepName(t.getStepName());
        vo.setNodeSeq(t.getNodeSeq());
        vo.setNodeName(t.getNodeName());
        vo.setParentNodeId(t.getParentNodeId());
        vo.setBranchPath(t.getBranchPath());
        vo.setDepth(t.getDepth());
        vo.setApproverId(t.getApproverId());
        vo.setApproverName(t.getApproverName());
        vo.setApproverSource(t.getApproverSource());
        vo.setDelegateOwnerName(t.getDelegateOwnerName());
        vo.setTaskKind(t.getTaskKind());
        vo.setStatus(t.getStatus());
        TaskStatus ts = TaskStatus.of(t.getStatus());
        vo.setStatusName(ts == null ? "" : ts.getLabel());
        vo.setApproveMode(t.getApproveMode());
        vo.setSeqInNode(t.getSeqInNode());
        vo.setOpinion(t.getOpinion());
        vo.setActedAt(t.getActedAt());
        vo.setCreatedAt(t.getCreatedAt());
        return vo;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readFormData(ApprInstance inst) {
        if (inst.getFormData() == null || inst.getFormData().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(inst.getFormData(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}

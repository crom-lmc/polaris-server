package com.laimengchao.sso.settings.module.approval.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.settings.common.SettingsErrorCode;
import com.laimengchao.sso.settings.module.approval.constant.ActionType;
import com.laimengchao.sso.settings.module.approval.constant.ApproveMode;
import com.laimengchao.sso.settings.module.approval.constant.InstanceStatus;
import com.laimengchao.sso.settings.module.approval.constant.TaskStatus;
import com.laimengchao.sso.settings.module.approval.dto.ApprovalActionRequest;
import com.laimengchao.sso.settings.module.approval.entity.ApprDelegate;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprTask;
import com.laimengchao.sso.settings.module.approval.mapper.ApprDelegateMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审批移交：转办、加签、抄送，以及委托设置。
 * <p>
 * 三者的差别，别混：
 * <ul>
 *   <li><b>转办</b>——把「这一条」任务永久给别人，我不再负责</li>
 *   <li><b>加签</b>——在「这一步」再插一个人，他必须也审（节点临时变成会签）</li>
 *   <li><b>委托</b>——一段时间内「所有」单子都自动派给别人，任务上仍记着我是原审批人（显示「代 X」）</li>
 * </ul>
 * 全部动作都写 appr_action，事后可追溯谁把单子挪走了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalMoveService {

    /** 单条任务最多转办 5 次，防止踢皮球 */
    private static final int MAX_TRANSFER = 5;

    private final ApprTaskMapper taskMapper;
    private final ApprDelegateMapper delegateMapper;
    private final ApprovalEngine engine;

    // ================================================================ 转办

    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long taskId, ApprovalActionRequest req) {
        ApprTask task = requirePending(taskId, req.getOperatorId());
        if (req.getTargetId() == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_FOUND, "转办目标不能为空");
        }
        if (req.getTargetId().equals(task.getApproverId())) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_FOUND, "不能转办给自己");
        }
        long transferCount = taskMapper.selectCount(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, task.getInstanceId())
                .eq(ApprTask::getStepSeq, task.getStepSeq())
                .eq(ApprTask::getNodeId, task.getNodeId()));
        if (transferCount > MAX_TRANSFER) {
            throw new BizException(SettingsErrorCode.APPROVAL_TRANSFER_LIMIT);
        }

        task.setStatus(TaskStatus.TRANSFERRED.getCode());
        task.setOpinion("转办：" + safe(req.getNote()));
        taskMapper.updateById(task);

        ApprTask next = copyTask(task);
        next.setApproverId(req.getTargetId());
        next.setApproverName(safe(req.getTargetName()));
        next.setApproverSource("USER");
        next.setDelegateOwnerId(null);
        next.setDelegateOwnerName("");
        taskMapper.insert(next);

        engine.logAction(task.getInstanceId(), task.getId(), task.getStepSeq(),
                req.getOperatorId(), req.getOperatorName(), ActionType.TRANSFER,
                req.getTargetId(), req.getTargetName(), req.getNote());
    }

    // ================================================================ 加签

    @Transactional(rollbackFor = Exception.class)
    public void addSign(Long taskId, ApprovalActionRequest req) {
        ApprTask task = requirePending(taskId, req.getOperatorId());
        if (req.getTargetId() == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_FOUND, "加签目标不能为空");
        }
        // 加签的人必须也审：把同节点尚在待办的任务整体转成会签语义，
        // 否则在「或签」节点上加签等于没加——原来的或签一人通过就把节点走完了。
        List<ApprTask> pending = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, task.getInstanceId())
                .eq(ApprTask::getStepSeq, task.getStepSeq())
                .eq(ApprTask::getNodeId, task.getNodeId())
                .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode()));
        for (ApprTask t : pending) {
            t.setApproveMode(ApproveMode.ALL.name());
            taskMapper.updateById(t);
        }

        ApprTask added = copyTask(task);
        added.setApproverId(req.getTargetId());
        added.setApproverName(safe(req.getTargetName()));
        added.setApproverSource("USER");
        added.setApproveMode(ApproveMode.ALL.name());
        // 前加签排在前面，后加签排在后面
        int base = task.getSeqInNode() == null ? 0 : task.getSeqInNode();
        added.setSeqInNode("BEFORE".equalsIgnoreCase(req.getPosition()) ? Math.max(base - 1, 0) : base + 1);
        taskMapper.insert(added);

        engine.logAction(task.getInstanceId(), task.getId(), task.getStepSeq(),
                req.getOperatorId(), req.getOperatorName(), ActionType.ADD_SIGN,
                req.getTargetId(), req.getTargetName(), req.getNote());
    }

    // ================================================================ 抄送

    @Transactional(rollbackFor = Exception.class)
    public void cc(Long instanceId, List<Long> accountIds, ApprovalActionRequest req) {
        ApprInstance inst = engine.require(instanceId);
        if (accountIds == null || accountIds.isEmpty()) {
            return;
        }
        for (Long id : accountIds) {
            ApprTask t = new ApprTask();
            t.setInstanceId(inst.getId());
            t.setStepSeq(inst.getCurrentStep() == null ? 0 : inst.getCurrentStep());
            t.setStepName("抄送");
            t.setNodeSeq(0);
            t.setNodeName("抄送");
            t.setApproverId(id);
            t.setApproverName("");
            t.setApproverSource("USER");
            t.setTaskKind("CC");
            // 抄送不阻塞流程，直接落成「已跳过」，只是留一条可见记录
            t.setStatus(TaskStatus.SKIPPED.getCode());
            t.setApproveMode(ApproveMode.ANY.name());
            t.setSeqInNode(0);
            t.setOpinion("");
            taskMapper.insert(t);
        }
        engine.logAction(instanceId, null, inst.getCurrentStep(), req.getOperatorId(),
                req.getOperatorName(), ActionType.CC, null, null, req.getNote());
    }

    // ================================================================ 委托

    public List<ApprDelegate> delegates(Long ownerId) {
        return delegateMapper.selectList(Wrappers.<ApprDelegate>lambdaQuery()
                .eq(ApprDelegate::getOwnerId, ownerId)
                .orderByDesc(ApprDelegate::getCreatedAt));
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprDelegate saveDelegate(ApprDelegate d, Long ownerId, String ownerName) {
        if (d.getStartTime() == null || d.getEndTime() == null || d.getEndTime().isBefore(d.getStartTime())) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "委托时间段不合法");
        }
        d.setOwnerId(ownerId);
        d.setOwnerName(safe(ownerName));
        d.setEnabled(d.getEnabled() == null ? 1 : d.getEnabled());
        if (d.getId() == null) {
            delegateMapper.insert(d);
        } else {
            ApprDelegate exist = delegateMapper.selectById(d.getId());
            if (exist == null || !ownerId.equals(exist.getOwnerId())) {
                throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_MINE, "只能修改自己的委托");
            }
            delegateMapper.updateById(d);
        }
        return d;
    }

    /**
     * 业务侧登记委托（如部门负责人请假期间代审）。
     * <p>
     * 按 ownerId + remark 幂等：审批回调可能重复投递，重复时只更新时间段与被委托人，
     * 不再插一条新的委托记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprDelegate upsertDelegate(ApprDelegate d) {
        if (d.getOwnerId() == null || d.getDelegateId() == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "委托人与被委托人不能为空");
        }
        if (d.getStartTime() == null || d.getEndTime() == null || d.getEndTime().isBefore(d.getStartTime())) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "委托时间段不合法");
        }
        d.setEnabled(d.getEnabled() == null ? 1 : d.getEnabled());
        String remark = d.getRemark() == null ? "" : d.getRemark().trim();
        ApprDelegate exist = remark.isEmpty() ? null : delegateMapper.selectOne(
                Wrappers.<ApprDelegate>lambdaQuery()
                        .eq(ApprDelegate::getOwnerId, d.getOwnerId())
                        .eq(ApprDelegate::getRemark, remark)
                        .last("limit 1"));
        if (exist == null) {
            delegateMapper.insert(d);
        } else {
            d.setId(exist.getId());
            delegateMapper.updateById(d);
        }
        return d;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDelegate(Long id, Long ownerId) {
        ApprDelegate exist = delegateMapper.selectById(id);
        if (exist == null) {
            return;
        }
        if (!ownerId.equals(exist.getOwnerId())) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_MINE, "只能删除自己的委托");
        }
        delegateMapper.deleteById(id);
    }

    // ================================================================ 工具

    private ApprTask requirePending(Long taskId, Long operatorId) {
        ApprTask task = engine.requireTask(taskId);
        if (!TaskStatus.PENDING.getCode().equals(task.getStatus())) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_DONE);
        }
        if (operatorId != null && !operatorId.equals(task.getApproverId())) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_MINE);
        }
        ApprInstance inst = engine.require(task.getInstanceId());
        if (!InstanceStatus.RUNNING.getCode().equals(inst.getStatus())) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_FINISHED);
        }
        return task;
    }

    private ApprTask copyTask(ApprTask src) {
        ApprTask t = new ApprTask();
        t.setInstanceId(src.getInstanceId());
        t.setStepSeq(src.getStepSeq());
        t.setStepName(src.getStepName());
        t.setNodeId(src.getNodeId());
        t.setNodeSeq(src.getNodeSeq());
        t.setNodeName(src.getNodeName());
        t.setApproveMode(src.getApproveMode());
        t.setSeqInNode(src.getSeqInNode());
        t.setTaskKind("APPROVE");
        t.setStatus(TaskStatus.PENDING.getCode());
        t.setOpinion("");
        return t;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}

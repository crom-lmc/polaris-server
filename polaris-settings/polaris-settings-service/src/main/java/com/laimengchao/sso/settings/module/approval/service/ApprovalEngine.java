package com.laimengchao.sso.settings.module.approval.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.settings.common.SettingsErrorCode;
import com.laimengchao.sso.settings.module.approval.constant.ActionType;
import com.laimengchao.sso.settings.module.approval.constant.ApproveMode;
import com.laimengchao.sso.settings.module.approval.constant.InstanceStatus;
import com.laimengchao.sso.settings.module.approval.constant.TaskStatus;
import com.laimengchao.sso.settings.module.approval.dto.ApprovalActionRequest;
import com.laimengchao.sso.settings.module.approval.dto.SceneTableBindingsDto;
import com.laimengchao.sso.settings.module.approval.dto.StartApprovalRequest;
import com.laimengchao.sso.settings.module.approval.entity.ApprAction;
import com.laimengchao.sso.settings.module.approval.entity.ApprDelegate;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprTask;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalNode;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalStep;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalTemplate;
import com.laimengchao.sso.settings.module.approval.entity.BizScene;
import com.laimengchao.sso.settings.module.approval.mapper.ApprActionMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprDelegateMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprInstanceMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprTaskMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalNodeMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalStepMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalTemplateMapper;
import com.laimengchao.sso.settings.module.approval.mapper.BizSceneMapper;
import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot;
import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot.ApproverRef;
import com.laimengchao.sso.settings.module.coderule.service.CodeRuleService;
import com.laimengchao.sso.settings.module.notice.dto.NoticeSendRequest;
import com.laimengchao.sso.settings.module.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 审批引擎：路径树（<=3 层条件网关） → 步骤串行 → 分支并行 → 分支内聚合。
 * <p>
 * 四条设计约束，改动前先读：
 * <ol>
 *   <li><b>只认快照</b>。发起时把模板结构复制进 instance.snapshot，之后引擎只读快照。</li>
 *   <li><b>只认任务表状态</b>。流程推进完全由 appr_task 的 status 推导。</li>
 *   <li><b>审批人后置解析</b>。快照存规则不存人，步骤激活时才解析。</li>
 *   <li><b>全实例屏障即分层汇合</b>。任何时刻只要 appr_task 还有 PENDING，就不允许汇合回父路径；
 *       归零时由最后完成的那条任务沿 branchPath 逐级向上回退，每层找「网关之后的下一个同路径步骤」。
 *       因此多层嵌套不需要额外的屏障表，也不会出现「外层先跑、内层还在跑」。</li>
 * </ol>
 * <p>
 * v5 语义（用户 2026-11-05 拍板）：AND 并行（命中即走、全部完成才汇合）+ 兜底分支 +
 * 任意节点驳回 = <b>整单 END</b>；不做 PREV / NODE 分叉，不做在途单旧快照兼容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEngine {

    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalStepMapper stepMapper;
    private final ApprovalNodeMapper nodeMapper;
    private final ApprInstanceMapper instanceMapper;
    private final ApprTaskMapper taskMapper;
    private final ApprActionMapper actionMapper;
    private final ApprDelegateMapper delegateMapper;
    private final BizSceneMapper sceneMapper;
    private final ApproverResolver approverResolver;
    private final ConditionEvaluator conditionEvaluator;
    private final ApprovalCallbackService callbackService;
    private final NoticeService noticeService;
    private final CodeRuleService codeRuleService;
    private final ObjectMapper objectMapper;

    // =================================================================== 发起

    @Transactional(rollbackFor = Exception.class)
    public ApprInstance start(StartApprovalRequest req) {
        if (isBlank(req.getBizType()) || isBlank(req.getBizId())) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "bizType / bizId 不能为空");
        }
        ApprovalTemplate tpl = templateMapper.selectOne(Wrappers.<ApprovalTemplate>lambdaQuery()
                .eq(ApprovalTemplate::getBizType, req.getBizType())
                .orderByDesc(ApprovalTemplate::getEnabled)
                .last("limit 1"));
        if (tpl == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_TEMPLATE_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(tpl.getEnabled())) {
            throw new BizException(SettingsErrorCode.APPROVAL_TEMPLATE_DISABLED);
        }
        Long running = instanceMapper.selectCount(Wrappers.<ApprInstance>lambdaQuery()
                .eq(ApprInstance::getBizType, req.getBizType())
                .eq(ApprInstance::getBizId, req.getBizId())
                .eq(ApprInstance::getStatus, InstanceStatus.RUNNING.getCode()));
        if (running != null && running > 0) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_RUNNING);
        }

        ApprovalSnapshot snap = buildSnapshot(tpl);

        ApprInstance inst = new ApprInstance();
        inst.setTemplateId(tpl.getId());
        inst.setTemplateCode(tpl.getCode());
        inst.setTemplateVersion(tpl.getVersion());
        inst.setBizType(req.getBizType());
        inst.setBizName(tpl.getBizName());
        inst.setBizId(req.getBizId());
        // 主体 ID 与 bizId 语义显式落库：列表反查、回调定位不再靠猜
        String[] subject = resolveSubject(req);
        inst.setSubjectId(subject[0]);
        inst.setBizIdKind(subject[1]);
        // 业务单号由调用方传入并原样保留；审批单自身的单号按「单号规则」生成
        inst.setBizNo(nullToEmpty(req.getBizNo()));
        inst.setApprNo(generateApprNo());
        inst.setTitle(nullToEmpty(req.getTitle()));
        inst.setApplicantId(req.getApplicantId());
        inst.setApplicantName(nullToEmpty(req.getApplicantName()));
        inst.setApplicantOrgId(req.getApplicantOrgId());
        inst.setOrgPath(nullToEmpty(req.getOrgPath()));
        inst.setFormData(writeJson(req.getFormData() == null ? Map.of() : req.getFormData()));
        inst.setSnapshot(writeJson(snap));
        // 第三份快照：把场景此刻的数据表绑定冻进实例。之后管理端改绑（增删表/改外键）
        // 只影响新发起的单，在途单仍按这份快照取数（用户 2026-11-06 要求）。
        inst.setTableBindings(currentTableBindings(req.getBizType()));
        inst.setStatus(InstanceStatus.RUNNING.getCode());
        inst.setCurrentStep(0);
        inst.setTotalStep(snap.getSteps().size());
        inst.setCallbackStatus(isBlank(req.getCallbackUrl()) ? 0 : 1);
        inst.setCallbackUrl(nullToEmpty(req.getCallbackUrl()));
        instanceMapper.insert(inst);

        logAction(inst.getId(), null, 0, req.getApplicantId(), req.getApplicantName(),
                ActionType.SUBMIT, null, null, "提交审批");

        activate(inst, snap, 1, "");
        return instanceMapper.selectById(inst.getId());
    }

    /**
     * 审批单自身的单号：按配置中心「单号规则」里 appr_instance 的规则生成。
     * 未配置规则返回空串；生成失败只记日志，不阻断发起（单号是展示用，不该影响主流程）。
     */
    private String generateApprNo() {
        try {
            return codeRuleService.nextCode(CodeRuleService.APPROVAL_TABLE);
        } catch (Exception e) {
            log.warn("审批单号生成失败，按空单号继续：{}", e.getMessage());
            return "";
        }
    }

    private static final Map<String, String> DEFAULT_SUBJECT_KEYS = Map.of(
            "ehr", "employeeId", "cms", "articleId", "blog", "articleId",
            "sso-center", "id", "settings", "id");

    /**
     * 解析「业务主体 ID」与 bizId 语义，返回 {subjectId, bizIdKind}。
     * <p>
     * 优先级：请求显式声明 → 场景配置 {@code cfg_biz_scene.subject_field} 从 formData 取值 →
     * 按 appCode 的默认键兜底 → 最后退回 bizId（主体即单据，存量口径）。
     * 因此新增申请单类业务只需在场景表配一行，不必再改 Java 常量。
     */
    private String[] resolveSubject(StartApprovalRequest req) {
        BizScene scene = sceneMapper.selectOne(Wrappers.<BizScene>lambdaQuery()
                .eq(BizScene::getBizType, req.getBizType())
                .last("limit 1"));
        Map<String, Object> form = req.getFormData() == null ? Map.of() : req.getFormData();
        String subjectId = isBlank(req.getSubjectId()) ? null : req.getSubjectId().trim();
        if (subjectId == null && scene != null && !isBlank(scene.getSubjectField())) {
            Object v = form.get(scene.getSubjectField().trim());
            if (v != null) {
                subjectId = String.valueOf(v).trim();
            }
        }
        if (subjectId == null) {
            String appCode = scene == null ? null : scene.getAppCode();
            String key = appCode == null || appCode.isBlank()
                    ? "employeeId"
                    : DEFAULT_SUBJECT_KEYS.getOrDefault(appCode.trim().toLowerCase(), "id");
            Object v = form.get(key);
            if (v != null) {
                subjectId = String.valueOf(v).trim();
            }
        }
        if (subjectId == null || subjectId.isEmpty()) {
            subjectId = req.getBizId();
        }
        String kind = isBlank(req.getBizIdKind()) ? null : req.getBizIdKind().trim().toUpperCase();
        if (kind == null && scene != null) {
            kind = isBlank(scene.getBizIdKind()) ? null : scene.getBizIdKind().trim().toUpperCase();
        }
        if (kind == null) {
            kind = subjectId.equals(req.getBizId()) ? "SUBJECT" : "DOC";
        }
        return new String[]{subjectId, kind};
    }

    /**
     * 取场景当前的数据表绑定作为实例快照。场景没配绑定则返回 null（该单不做真绑定）。
     * <p>
     * <b>冻结时补 appCode</b>：绑定 JSON 里记的是「哪个应用的哪些表」，取数要按它路由到对应库。
     * 老配置（加 appCode 之前保存的）里没有这个字段，这里用场景的 appCode 补齐后一起冻结，
     * 保证快照自包含——事后改场景所属应用，在途单仍回原库取数。
     * <p>
     * 只复制字符串，不校验表是否存在——配置写坏了也不能挡住发起，渲染期会按「取不到」降级。
     */
    private String currentTableBindings(String bizType) {
        BizScene scene = sceneMapper.selectOne(Wrappers.<BizScene>lambdaQuery()
                .eq(BizScene::getBizType, bizType)
                .last("limit 1"));
        if (scene == null || isBlank(scene.getTableBindings())) {
            return null;
        }
        try {
            SceneTableBindingsDto bind = objectMapper.readValue(scene.getTableBindings(), SceneTableBindingsDto.class);
            if (isBlank(bind.getAppCode())) {
                bind.setAppCode(scene.getAppCode());
            }
            return objectMapper.writeValueAsString(bind);
        } catch (Exception e) {
            // 解析不了就原样冻结：渲染端同样会解析失败并降级，不会挡住流程推进
            log.warn("场景 {} 的数据表绑定无法解析，按原文冻结：{}", bizType, e.getMessage());
            return scene.getTableBindings();
        }
    }

    /** 模板 → 快照。steps 已按 DFS 前序编号，路径字段原样冻结。 */
    private ApprovalSnapshot buildSnapshot(ApprovalTemplate tpl) {
        List<ApprovalStep> steps = stepMapper.selectList(Wrappers.<ApprovalStep>lambdaQuery()
                .eq(ApprovalStep::getTemplateId, tpl.getId())
                .orderByAsc(ApprovalStep::getSeq));
        if (steps.isEmpty()) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "模板「" + tpl.getName() + "」还没有配置任何步骤");
        }
        List<ApprovalNode> nodes = nodeMapper.selectList(Wrappers.<ApprovalNode>lambdaQuery()
                .eq(ApprovalNode::getTemplateId, tpl.getId())
                .orderByAsc(ApprovalNode::getSeq));

        ApprovalSnapshot snap = new ApprovalSnapshot();
        snap.setVersion(tpl.getVersion());
        for (ApprovalStep s : steps) {
            ApprovalSnapshot.Step ss = new ApprovalSnapshot.Step();
            ss.setSeq(s.getSeq());
            ss.setName(s.getName());
            ss.setConditionExpr(nullToEmpty(s.getConditionExpr()));
            // v5：驳回统一整单 END，忽略模板里残留的 PREV / NODE
            ss.setRejectAction("END");
            ss.setBranchPath(nullToEmpty(s.getBranchPath()));
            ss.setParentNodeId(s.getParentNodeId());
            ss.setDepth(s.getDepth() == null ? 0 : s.getDepth());
            ss.setUiKind(nullToEmpty(s.getUiKind()));
            for (ApprovalNode n : nodes) {
                if (!s.getId().equals(n.getStepId())) {
                    continue;
                }
                ApprovalSnapshot.Node sn = new ApprovalSnapshot.Node();
                sn.setNodeId(n.getId());
                sn.setSeq(n.getSeq());
                sn.setName(nullToEmpty(n.getName()));
                sn.setConditionExpr(nullToEmpty(n.getConditionExpr()));
                sn.setIsFallback(Integer.valueOf(1).equals(n.getIsFallback()));
                sn.setApproveMode(nullToEmpty(n.getApproveMode()).isEmpty() ? "ANY" : n.getApproveMode());
                sn.setAllowSelf(n.getAllowSelf() == null ? 0 : n.getAllowSelf());
                sn.setSkipSame(n.getSkipSame() == null ? 1 : n.getSkipSame());
                sn.setOnEmpty(nullToEmpty(n.getOnEmpty()).isEmpty() ? "SKIP" : n.getOnEmpty());
                sn.setApprovers(parseRules(n.getApprovers()));
                ss.getNodes().add(sn);
            }
            if (ss.getNodes().isEmpty()) {
                throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID,
                        "步骤「" + s.getName() + "」下没有任何审批分支");
            }
            ss.getNodes().sort(Comparator.comparing(ApprovalSnapshot.Node::getSeq));
            snap.getSteps().add(ss);
        }
        snap.getSteps().sort(Comparator.comparing(ApprovalSnapshot.Step::getSeq));
        return snap;
    }

    private List<ApprovalSnapshot.ApproverRule> parseRules(String json) {
        if (isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ApprovalSnapshot.ApproverRule>>() {
            });
        } catch (Exception e) {
            log.warn("审批人规则解析失败：{}", json);
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "审批人规则格式不合法");
        }
    }

    // =================================================================== 推进

    /**
     * 在指定路径上从 fromSeq 开始找第一个能派单的步骤。
     * 条件不满足 / 无人生效的步骤记 SKIP 后继续；整条路径都派不出单时交给 tryAdvance 汇合。
     */
    private void activate(ApprInstance inst, ApprovalSnapshot snap, int fromSeq, String path) {
        for (ApprovalSnapshot.Step step : stepsOf(snap, path)) {
            if (step.getSeq() == null || step.getSeq() < fromSeq) {
                continue;
            }
            Map<String, Object> vars = formVars(inst);
            if (!conditionEvaluator.eval(step.getConditionExpr(), vars)) {
                logAction(inst.getId(), null, step.getSeq(), null, null,
                        ActionType.SKIP, null, null, "步骤条件不满足：" + nullToEmpty(step.getConditionExpr()));
                continue;
            }
            if (createStepTasks(inst, step, vars)) {
                inst.setCurrentStep(step.getSeq());
                instanceMapper.updateById(inst);
                return;
            }
            logAction(inst.getId(), null, step.getSeq(), null, null,
                    ActionType.SKIP, null, null, "该步骤所有分支均未生效或无人可审");
        }
        if (path.isEmpty()) {
            finish(inst, InstanceStatus.APPROVED);
        } else {
            tryAdvance(inst, snap, path, fromSeq);
        }
    }

    /** 为一个步骤的所有生效分支派单；返回是否有任务被创建 */
    private boolean createStepTasks(ApprInstance inst, ApprovalSnapshot.Step step, Map<String, Object> vars) {
        List<ApprovalSnapshot.Node> hit = new ArrayList<>();
        if (step.isGateway()) {
            // AND 并行：所有条件为真的非兜底分支全部激活
            for (ApprovalSnapshot.Node n : step.getNodes()) {
                if (!Boolean.TRUE.equals(n.getIsFallback()) && conditionEvaluator.eval(n.getConditionExpr(), vars)) {
                    hit.add(n);
                }
            }
            // 零命中时兜底生效；有命中时兜底完全不参与
            if (hit.isEmpty()) {
                for (ApprovalSnapshot.Node n : step.getNodes()) {
                    if (Boolean.TRUE.equals(n.getIsFallback())) {
                        hit.add(n);
                    }
                }
            }
        } else {
            // 普通步骤 / 并行会签：按节点条件逐个判定
            for (ApprovalSnapshot.Node n : step.getNodes()) {
                if (conditionEvaluator.eval(n.getConditionExpr(), vars)) {
                    hit.add(n);
                }
            }
        }
        boolean any = false;
        for (ApprovalSnapshot.Node node : hit) {
            boolean allowSelf = Integer.valueOf(1).equals(node.getAllowSelf());
            List<ApproverRef> people = approverResolver.resolve(node.getApprovers(),
                    inst.getApplicantId(), inst.getApplicantOrgId(), allowSelf, vars);
            if (people.isEmpty()) {
                if ("BLOCK".equalsIgnoreCase(node.getOnEmpty())) {
                    throw new BizException(SettingsErrorCode.APPROVAL_APPROVER_EMPTY,
                            "步骤「" + step.getName() + "」的审批人解析为空");
                }
                logAction(inst.getId(), null, step.getSeq(), null, node.getName(),
                        ActionType.SKIP, null, null, "未解析到审批人，分支跳过");
                continue;
            }
            ApproveMode mode = ApproveMode.of(node.getApproveMode());
            int count = mode == ApproveMode.SEQ ? 1 : people.size();
            for (int i = 0; i < count; i++) {
                createTask(inst, step, node, people.get(i), i, mode);
            }
            any = true;
        }
        return any;
    }

    private void createTask(ApprInstance inst, ApprovalSnapshot.Step step, ApprovalSnapshot.Node node,
                            ApproverRef person, int seqInNode, ApproveMode mode) {
        ApprTask task = new ApprTask();
        task.setInstanceId(inst.getId());
        task.setStepSeq(step.getSeq());
        task.setStepName(step.getName());
        task.setNodeId(node.getNodeId());
        task.setNodeSeq(node.getSeq());
        task.setNodeName(node.getName());
        // 网关分支卡标记「自己是哪条分支」；子链任务标记所属路径的最后一段
        task.setParentNodeId(step.isGateway() ? node.getNodeId()
                : ApprovalSnapshot.lastNodeId(step.path()));
        task.setBranchPath(step.path());
        task.setDepth(step.depth());
        task.setApproveMode(mode.name());
        task.setSeqInNode(seqInNode);
        task.setTaskKind("APPROVE");
        task.setStatus(TaskStatus.PENDING.getCode());
        task.setOpinion("");
        applyDelegate(inst, task, person);
        taskMapper.insert(task);
        notifyApprover(inst, task);
    }

    /** 委托代收：owner 在有效期内把审批权交给 delegate，任务直接落到被委托人身上 */
    private void applyDelegate(ApprInstance inst, ApprTask task, ApproverRef person) {
        task.setApproverId(person.getId());
        task.setApproverName(nullToEmpty(person.getName()));
        task.setApproverSource("USER");
        LocalDateTime now = LocalDateTime.now();
        List<ApprDelegate> ds = delegateMapper.selectList(Wrappers.<ApprDelegate>lambdaQuery()
                .eq(ApprDelegate::getOwnerId, person.getId())
                .eq(ApprDelegate::getEnabled, 1)
                .le(ApprDelegate::getStartTime, now)
                .ge(ApprDelegate::getEndTime, now));
        for (ApprDelegate d : ds) {
            if (!isBlank(d.getBizType()) && !d.getBizType().equals(inst.getBizType())) {
                continue;
            }
            task.setApproverId(d.getDelegateId());
            task.setApproverName(nullToEmpty(d.getDelegateName()));
            task.setApproverSource("DELEGATE");
            task.setDelegateOwnerId(person.getId());
            task.setDelegateOwnerName(nullToEmpty(person.getName()));
            return;
        }
    }

    // =================================================================== 审批

    @Transactional(rollbackFor = Exception.class)
    public ApprInstance approve(Long taskId, ApprovalActionRequest req) {
        ApprTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_FOUND);
        }
        if (!TaskStatus.PENDING.getCode().equals(task.getStatus())) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_DONE);
        }
        if (req.getOperatorId() != null && !req.getOperatorId().equals(task.getApproverId())) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_MINE);
        }
        // 多层分支下两条尾任务可能同时触发汇合，先锁实例行把「读状态→改任务→推进」串行化
        ApprInstance inst = instanceMapper.selectForUpdate(task.getInstanceId());
        if (inst == null) {
            inst = instanceMapper.selectById(task.getInstanceId());
        }
        if (inst == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_NOT_FOUND);
        }
        if (!InstanceStatus.RUNNING.getCode().equals(inst.getStatus())) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_FINISHED);
        }
        ApprovalSnapshot snap = readSnapshot(inst);
        boolean pass = req.getPass() == null || req.getPass();

        task.setStatus(pass ? TaskStatus.APPROVED.getCode() : TaskStatus.REJECTED.getCode());
        task.setOpinion(nullToEmpty(req.getNote()));
        task.setActedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        logAction(inst.getId(), task.getId(), task.getStepSeq(), req.getOperatorId(),
                req.getOperatorName(), pass ? ActionType.APPROVE : ActionType.REJECT,
                null, null, req.getNote());

        if (pass) {
            onPass(inst, snap, task);
        } else {
            onReject(inst, snap, task);
        }
        return instanceMapper.selectById(inst.getId());
    }

    private void onPass(ApprInstance inst, ApprovalSnapshot snap, ApprTask task) {
        ApprovalSnapshot.Step step = findStep(snap, task.getStepSeq());
        if (step == null) {
            finish(inst, InstanceStatus.APPROVED);
            return;
        }
        ApprovalSnapshot.Node node = findNode(step, task.getNodeId());
        ApproveMode mode = node == null ? ApproveMode.ANY : ApproveMode.of(node.getApproveMode());

        // 1) 分支内聚合：或签 / 会签 / 依次
        switch (mode) {
            case ANY -> {
                for (ApprTask s : siblings(inst.getId(), task.getStepSeq(), task.getNodeId())) {
                    if (!s.getId().equals(task.getId()) && TaskStatus.PENDING.getCode().equals(s.getStatus())) {
                        s.setStatus(TaskStatus.CANCELLED.getCode());
                        s.setOpinion("或签已由他人处理");
                        taskMapper.updateById(s);
                    }
                }
            }
            case SEQ -> {
                if (node != null) {
                    boolean allowSelf = Integer.valueOf(1).equals(node.getAllowSelf());
                    List<ApproverRef> people = approverResolver.resolve(node.getApprovers(),
                            inst.getApplicantId(), inst.getApplicantOrgId(), allowSelf, formVars(inst));
                    int next = (task.getSeqInNode() == null ? 0 : task.getSeqInNode()) + 1;
                    if (next < people.size()) {
                        createTask(inst, step, node, people.get(next), next, mode);
                        return;
                    }
                }
            }
            case ALL -> {
                if (countPending(inst.getId(), task.getStepSeq(), task.getNodeId()) > 0) {
                    return;
                }
            }
            default -> {
            }
        }

        String path = step.path();
        // 2) 网关分支卡通过 → 进入该分支自己的子链（每条分支独立推进，互不阻塞）
        if (step.isGateway() && task.getParentNodeId() != null) {
            String child = ApprovalSnapshot.childPath(path, task.getParentNodeId());
            ApprovalSnapshot.Step first = firstStepOfPath(snap, child);
            if (first != null && first.getSeq() != null) {
                activate(inst, snap, first.getSeq(), child);
                return;
            }
        }

        // 3) 同一步骤还有其它分支卡 / 并行节点未完成 → 等
        if (countPendingOfStep(inst.getId(), task.getStepSeq()) > 0) {
            return;
        }

        // 4) 网关：所有分支卡已完成，必须等全部子链跑完（全实例屏障）才允许回主干
        if (step.isGateway()) {
            if (countPendingAll(inst.getId()) > 0) {
                return;
            }
            tryAdvance(inst, snap, path, step.getSeq());
            return;
        }

        // 5) 普通步骤：走同路径的下一步；没有下一步则触发汇合
        ApprovalSnapshot.Step next = firstStepAfter(snap, step.getSeq(), path);
        if (next != null && next.getSeq() != null) {
            activate(inst, snap, next.getSeq(), path);
        } else {
            tryAdvance(inst, snap, path, step.getSeq());
        }
    }

    /**
     * 分层汇合：全实例无 PENDING 时，沿 branchPath 逐级向上回退，
     * 每一层找「产生这条分支的网关之后的第一个同路径步骤」。
     * 找到就激活，一路回到主干仍没有则整单通过。
     */
    private void tryAdvance(ApprInstance inst, ApprovalSnapshot snap, String path, int fromSeq) {
        if (countPendingAll(inst.getId()) > 0) {
            return;   // 其它分支仍在跑，本分支进入「已通过 · 等待汇合」
        }
        String p = path == null ? "" : path;
        int from = fromSeq;
        for (int guard = 0; guard < 10; guard++) {
            ApprovalSnapshot.Step next = firstStepAfter(snap, from, p);
            if (next != null && next.getSeq() != null) {
                activate(inst, snap, next.getSeq(), p);
                return;
            }
            if (p.isEmpty()) {
                finish(inst, InstanceStatus.APPROVED);
                return;
            }
            Long nodeId = ApprovalSnapshot.lastNodeId(p);
            ApprovalSnapshot.Step gateway = findStepByNode(snap, nodeId);
            p = ApprovalSnapshot.parentPath(p);
            from = gateway == null || gateway.getSeq() == null ? 0 : gateway.getSeq();
        }
        log.warn("汇合回退超过 10 层，路径可能非法：instance={} path={}", inst.getId(), path);
        finish(inst, InstanceStatus.APPROVED);
    }

    /** v5：任何节点驳回都作废全实例待办并终止整单，不做 PREV / NODE 分叉 */
    private void onReject(ApprInstance inst, ApprovalSnapshot snap, ApprTask task) {
        cancelAll(inst.getId(), "流程驳回，任务作废");
        logAction(inst.getId(), task.getId(), task.getStepSeq(), null, null,
                ActionType.REJECT, null, null, "驳回，整单终止（整单 END）");
        finish(inst, InstanceStatus.REJECTED);
    }

    // =================================================================== 快照寻址

    private List<ApprovalSnapshot.Step> stepsOf(ApprovalSnapshot snap, String path) {
        List<ApprovalSnapshot.Step> list = new ArrayList<>();
        for (ApprovalSnapshot.Step s : snap.getSteps()) {
            if ((path == null ? "" : path).equals(s.path())) {
                list.add(s);
            }
        }
        list.sort(Comparator.comparing(ApprovalSnapshot.Step::getSeq,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    private ApprovalSnapshot.Step firstStepOfPath(ApprovalSnapshot snap, String path) {
        for (ApprovalSnapshot.Step s : stepsOf(snap, path)) {
            return s;
        }
        return null;
    }

    private ApprovalSnapshot.Step firstStepAfter(ApprovalSnapshot snap, int seq, String path) {
        for (ApprovalSnapshot.Step s : stepsOf(snap, path)) {
            if (s.getSeq() != null && s.getSeq() > seq) {
                return s;
            }
        }
        return null;
    }

    /** 反查：哪个步骤（网关）含有这个分支节点 */
    private ApprovalSnapshot.Step findStepByNode(ApprovalSnapshot snap, Long nodeId) {
        if (nodeId == null) {
            return null;
        }
        for (ApprovalSnapshot.Step s : snap.getSteps()) {
            for (ApprovalSnapshot.Node n : s.getNodes()) {
                if (nodeId.equals(n.getNodeId())) {
                    return s;
                }
            }
        }
        return null;
    }

    // =================================================================== 计数

    private long countPendingAll(Long instanceId) {
        Long c = taskMapper.selectCount(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, instanceId)
                .eq(ApprTask::getTaskKind, "APPROVE")
                .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode()));
        return c == null ? 0 : c;
    }

    private long countPendingOfStep(Long instanceId, int stepSeq) {
        Long c = taskMapper.selectCount(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, instanceId)
                .eq(ApprTask::getStepSeq, stepSeq)
                .eq(ApprTask::getTaskKind, "APPROVE")
                .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode()));
        return c == null ? 0 : c;
    }

    private long countPending(Long instanceId, int stepSeq, Long nodeId) {
        Long c = taskMapper.selectCount(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, instanceId)
                .eq(ApprTask::getStepSeq, stepSeq)
                .eq(ApprTask::getNodeId, nodeId)
                .eq(ApprTask::getTaskKind, "APPROVE")
                .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode()));
        return c == null ? 0 : c;
    }

    private List<ApprTask> siblings(Long instanceId, int stepSeq, Long nodeId) {
        return taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, instanceId)
                .eq(ApprTask::getStepSeq, stepSeq)
                .eq(ApprTask::getNodeId, nodeId)
                .eq(ApprTask::getTaskKind, "APPROVE"));
    }

    // =================================================================== 终态

    private void finish(ApprInstance inst, InstanceStatus status) {
        if (status.getCode().equals(inst.getStatus()) && inst.getFinishedAt() != null) {
            return;   // 幂等：多条尾任务串行触发时不重复回调业务方
        }
        inst.setStatus(status.getCode());
        inst.setFinishedAt(LocalDateTime.now());
        if (!isBlank(inst.getCallbackUrl())) {
            inst.setCallbackStatus(1);
        }
        instanceMapper.updateById(inst);
        if (!isBlank(inst.getCallbackUrl())) {
            callbackService.enqueue(inst);
        }
        log.info("审批单 {} 结束：{}", inst.getId(), status.getLabel());
        // 单据已办结：把该单在消息中心里的待办一并消掉，避免审批完了还挂着
        noticeService.markBizHandled(inst.getBizType(), String.valueOf(inst.getId()));
        notifyApplicant(inst, status);
    }

    /** 审批结果通知发起人：只处理通过/驳回，撤回与终止属发起人自己的操作，不打扰 */
    private void notifyApplicant(ApprInstance inst, InstanceStatus status) {
        boolean approved = InstanceStatus.APPROVED.getCode().equals(status.getCode());
        boolean rejected = InstanceStatus.REJECTED.getCode().equals(status.getCode());
        if ((!approved && !rejected) || inst.getApplicantId() == null) {
            return;
        }
        try {
            String opinion = "";
            // 操作人：驳回取驳回人，通过取最后一位实际通过的人（多级审批即终审人）
            String operatorName = "";
            if (rejected) {
                List<ApprTask> rejectedTasks = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                        .eq(ApprTask::getInstanceId, inst.getId())
                        .eq(ApprTask::getStatus, TaskStatus.REJECTED.getCode())
                        .orderByDesc(ApprTask::getActedAt)
                        .last("limit 1"));
                if (!rejectedTasks.isEmpty()) {
                    opinion = nullToEmpty(rejectedTasks.get(0).getOpinion());
                    operatorName = nullToEmpty(rejectedTasks.get(0).getApproverName());
                }
            } else {
                List<ApprTask> approvedTasks = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                        .eq(ApprTask::getInstanceId, inst.getId())
                        .eq(ApprTask::getTaskKind, "APPROVE")
                        .eq(ApprTask::getStatus, TaskStatus.APPROVED.getCode())
                        .orderByDesc(ApprTask::getActedAt)
                        .last("limit 1"));
                if (!approvedTasks.isEmpty()) {
                    operatorName = nullToEmpty(approvedTasks.get(0).getApproverName());
                }
            }
            NoticeSendRequest req = new NoticeSendRequest();
            req.setRuleCode(approved ? "approval.approved" : "approval.rejected");
            req.setAccountId(inst.getApplicantId());
            req.setBizType(inst.getBizType());
            // 消息中心的待办/消息要能直接打开审批抽屉，这里存审批单ID而不是业务ID
            req.setBizId(String.valueOf(inst.getId()));
            // 消息中心「业务编码」列展示审批单自身的单号
            req.setBizNo(nullToEmpty(inst.getApprNo()));
            req.setApplicantName(operatorName);
            req.setLink("/approvals");
            req.getVars().put("bizName", nullToEmpty(inst.getBizName()));
            req.getVars().put("title", nullToEmpty(inst.getTitle()));
            req.getVars().put("bizNo", nullToEmpty(inst.getApprNo()));
            req.getVars().put("applicantName", nullToEmpty(inst.getApplicantName()));
            req.getVars().put("opinion", opinion);
            noticeService.send(req);
        } catch (Exception ex) {
            log.warn("审批结果通知发送失败：instanceId={} err={}", inst.getId(), ex.getMessage());
        }
    }

    /** 新待办通知审批人；渠道（待办/消息）与是否发邮件由配置页决定 */
    private void notifyApprover(ApprInstance inst, ApprTask task) {
        if (task.getApproverId() == null) {
            return;
        }
        try {
            NoticeSendRequest req = new NoticeSendRequest();
            req.setRuleCode("approval.task");
            req.setAccountId(task.getApproverId());
            req.setBizType(inst.getBizType());
            req.setBizId(String.valueOf(inst.getId()));
            req.setBizNo(nullToEmpty(inst.getApprNo()));
            // 待办的「发起人」列展示申请人本人
            req.setApplicantName(nullToEmpty(inst.getApplicantName()));
            req.setLink("/approvals");
            req.getVars().put("bizName", nullToEmpty(inst.getBizName()));
            req.getVars().put("title", nullToEmpty(inst.getTitle()));
            req.getVars().put("bizNo", nullToEmpty(inst.getApprNo()));
            req.getVars().put("applicantName", nullToEmpty(inst.getApplicantName()));
            noticeService.send(req);
        } catch (Exception ex) {
            log.warn("审批待办通知发送失败：instanceId={} approverId={} err={}",
                    inst.getId(), task.getApproverId(), ex.getMessage());
        }
    }

    // =================================================================== 撤回 / 终止

    @Transactional(rollbackFor = Exception.class)
    public ApprInstance withdraw(Long instanceId, ApprovalActionRequest req) {
        ApprInstance inst = require(instanceId);
        if (!InstanceStatus.RUNNING.getCode().equals(inst.getStatus())) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_FINISHED);
        }
        if (req.getOperatorId() != null && !req.getOperatorId().equals(inst.getApplicantId())) {
            throw new BizException(SettingsErrorCode.APPROVAL_NOT_APPLICANT);
        }
        cancelAll(instanceId, "发起人撤回");
        logAction(instanceId, null, inst.getCurrentStep(), req.getOperatorId(), req.getOperatorName(),
                ActionType.WITHDRAW, null, null, req.getNote());
        finish(inst, InstanceStatus.WITHDRAWN);
        return inst;
    }

    @Transactional(rollbackFor = Exception.class)
    public ApprInstance terminate(Long instanceId, ApprovalActionRequest req) {
        ApprInstance inst = require(instanceId);
        if (!InstanceStatus.RUNNING.getCode().equals(inst.getStatus())) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_FINISHED);
        }
        cancelAll(instanceId, "管理员终止");
        logAction(instanceId, null, inst.getCurrentStep(), req.getOperatorId(), req.getOperatorName(),
                ActionType.TERMINATE, null, null, req.getNote());
        finish(inst, InstanceStatus.TERMINATED);
        return inst;
    }

    private void cancelAll(Long instanceId, String reason) {
        List<ApprTask> list = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, instanceId)
                .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode()));
        for (ApprTask t : list) {
            t.setStatus(TaskStatus.CANCELLED.getCode());
            t.setOpinion(reason);
            taskMapper.updateById(t);
        }
    }

    // =================================================================== 工具

    public ApprInstance require(Long instanceId) {
        ApprInstance inst = instanceMapper.selectById(instanceId);
        if (inst == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_NOT_FOUND);
        }
        return inst;
    }

    public ApprTask requireTask(Long taskId) {
        ApprTask t = taskMapper.selectById(taskId);
        if (t == null) {
            throw new BizException(SettingsErrorCode.APPROVAL_TASK_NOT_FOUND);
        }
        return t;
    }

    public ApprovalSnapshot readSnapshot(ApprInstance inst) {
        try {
            return objectMapper.readValue(inst.getSnapshot(), ApprovalSnapshot.class);
        } catch (Exception e) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "流程快照损坏，无法推进");
        }
    }

    public Map<String, Object> formVars(ApprInstance inst) {
        if (isBlank(inst.getFormData())) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(inst.getFormData(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private ApprovalSnapshot.Step findStep(ApprovalSnapshot snap, Integer seq) {
        if (seq == null) {
            return null;
        }
        for (ApprovalSnapshot.Step s : snap.getSteps()) {
            if (seq.equals(s.getSeq())) {
                return s;
            }
        }
        return null;
    }

    private ApprovalSnapshot.Node findNode(ApprovalSnapshot.Step step, Long nodeId) {
        if (step == null || nodeId == null) {
            return null;
        }
        for (ApprovalSnapshot.Node n : step.getNodes()) {
            if (nodeId.equals(n.getNodeId())) {
                return n;
            }
        }
        return null;
    }

    public void logAction(Long instanceId, Long taskId, Integer stepSeq, Long actorId, String actorName,
                          ActionType action, Long targetId, String targetName, String opinion) {
        ApprAction a = new ApprAction();
        a.setInstanceId(instanceId);
        a.setTaskId(taskId);
        a.setStepSeq(stepSeq == null ? 0 : stepSeq);
        a.setActorId(actorId);
        a.setActorName(nullToEmpty(actorName));
        a.setAction(action.name());
        a.setTargetId(targetId);
        a.setTargetName(nullToEmpty(targetName));
        a.setOpinion(nullToEmpty(opinion));
        actionMapper.insert(a);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException(SettingsErrorCode.APPROVAL_STEP_INVALID, "序列化失败");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}

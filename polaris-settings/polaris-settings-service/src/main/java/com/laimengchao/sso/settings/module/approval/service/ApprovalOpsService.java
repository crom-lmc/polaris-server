package com.laimengchao.sso.settings.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.settings.common.SettingsErrorCode;
import com.laimengchao.sso.settings.module.approval.constant.ActionType;
import com.laimengchao.sso.settings.module.approval.constant.InstanceStatus;
import com.laimengchao.sso.settings.module.approval.constant.TaskStatus;
import com.laimengchao.sso.settings.module.approval.dto.ApprovalActionRequest;
import com.laimengchao.sso.settings.module.approval.dto.BatchApprovalRequest;
import com.laimengchao.sso.settings.module.approval.entity.ApprAction;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprTask;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalTemplate;
import com.laimengchao.sso.settings.module.approval.mapper.ApprActionMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprInstanceMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprTaskMapper;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalTemplateMapper;
import com.laimengchao.sso.settings.module.approval.model.ApprovalSnapshot;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalApproverStatsVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalBacklogVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalStatsVO;
import com.laimengchao.sso.settings.module.approval.vo.BatchApprovalResultVO;
import com.laimengchao.sso.settings.module.notice.dto.NoticeSendRequest;
import com.laimengchao.sso.settings.module.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审批运营：催办、SLA 超时提醒、时效统计、批量审批、待办/流水导出。
 * <p>
 * 与 {@link ApprovalEngine} 的分工：引擎只管「单子怎么走」，这里管「走得快不快」——
 * 不改流程状态，只读实例/任务、写催办与提醒流水。批量审批逐条调用引擎，
 * 每条独立事务，部分失败不回滚已成功的单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalOpsService {

    /** 批次查询任务时每片实例 ID 数量，避免一条 SQL 拼得过长 */
    private static final int CHUNK = 500;

    private final ApprInstanceMapper instanceMapper;
    private final ApprTaskMapper taskMapper;
    private final ApprActionMapper actionMapper;
    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalEngine engine;
    private final NoticeService noticeService;
    private final ObjectMapper objectMapper;

    // ================================================================ 催办

    /**
     * 催办：只对进行中的单生效，次数累加并写 appr_action(action=URGE)，
     * 同时给当前所有待办人发一条站内消息。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprInstance urge(Long instanceId, ApprovalActionRequest req) {
        ApprInstance inst = engine.require(instanceId);
        if (!InstanceStatus.RUNNING.getCode().equals(inst.getStatus())) {
            throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_FINISHED);
        }
        LocalDateTime now = LocalDateTime.now();
        // 用 SQL 自增，避免并发两连点只加一次
        instanceMapper.update(null, Wrappers.<ApprInstance>lambdaUpdate()
                .eq(ApprInstance::getId, instanceId)
                .setSql("urge_count = IFNULL(urge_count, 0) + 1")
                .set(ApprInstance::getLastUrgeAt, now));
        engine.logAction(instanceId, null, inst.getCurrentStep(), req.getOperatorId(),
                req.getOperatorName(), ActionType.URGE, null, null, req.getNote());

        List<ApprTask> pending = pendingTasks(instanceId);
        String stepName = pending.isEmpty() ? "" : nullToEmpty(pending.get(0).getStepName());
        notifyApprovers(inst, pending, "approval.urge",
                "催办提醒：" + nullToEmpty(inst.getBizName()),
                "你有一条待审批单据「" + nullToEmpty(inst.getTitle()) + "」（" + stepName
                        + "），请尽快处理。");
        return instanceMapper.selectById(instanceId);
    }

    // ================================================================ SLA 扫描

    /**
     * 扫描当前步骤停留超过 sla_hours 的在途单，给待办人发提醒。
     * 同一实例每天最多提醒一次（按 appr_action 里当天的 SLA_REMIND 流水去重）。
     * 返回本次提醒的实例数，供定时任务日志使用。
     */
    public int scanSla() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        List<ApprInstance> running = instanceMapper.selectList(Wrappers.<ApprInstance>lambdaQuery()
                .eq(ApprInstance::getStatus, InstanceStatus.RUNNING.getCode()));
        int count = 0;
        for (ApprInstance inst : running) {
            ApprovalSnapshot.Step step = currentStep(inst);
            if (step == null || step.getSlaHours() == null || step.getSlaHours() <= 0) {
                continue;
            }
            List<ApprTask> pending = pendingTasks(inst.getId());
            if (pending.isEmpty()) {
                continue;
            }
            LocalDateTime since = pending.stream().map(ApprTask::getCreatedAt)
                    .filter(x -> x != null).min(Comparator.naturalOrder())
                    .orElse(inst.getCreatedAt());
            if (since == null || hours(since, now) <= step.getSlaHours()) {
                continue;
            }
            Long reminded = actionMapper.selectCount(Wrappers.<ApprAction>lambdaQuery()
                    .eq(ApprAction::getInstanceId, inst.getId())
                    .eq(ApprAction::getAction, ActionType.SLA_REMIND.name())
                    .ge(ApprAction::getCreatedAt, dayStart));
            if (reminded != null && reminded > 0) {
                continue;
            }
            long waiting = (long) Math.floor(hours(since, now));
            notifyApprovers(inst, pending, "approval.sla",
                    "审批超时提醒：" + nullToEmpty(inst.getBizName()),
                    "单据「" + nullToEmpty(inst.getTitle()) + "」在「" + nullToEmpty(step.getName())
                            + "」已停留 " + waiting + " 小时，超过时效 " + step.getSlaHours() + " 小时，请尽快处理。");
            engine.logAction(inst.getId(), null, inst.getCurrentStep(), null, "系统",
                    ActionType.SLA_REMIND, null, null, "停留 " + waiting + " 小时，超过 SLA " + step.getSlaHours() + " 小时");
            count++;
        }
        if (count > 0) {
            log.info("审批 SLA 扫描：本次提醒 {} 张在途单", count);
        }
        return count;
    }

    // ================================================================ 统计

    /** 场景 / 组织维度的审批时效统计 */
    public ApprovalStatsVO stats(String appCode, String bizType, Long orgId, LocalDate from, LocalDate to) {
        List<ApprInstance> list = queryInstances(appCode, bizType, orgId, from, to, null);
        ApprovalStatsVO vo = new ApprovalStatsVO();
        if (list == null || list.isEmpty()) {
            return vo;
        }
        long approved = 0;
        long rejected = 0;
        long withdrawn = 0;
        long terminated = 0;
        long backlog = 0;
        List<Double> durations = new ArrayList<>();
        for (ApprInstance i : list) {
            if (InstanceStatus.RUNNING.getCode().equals(i.getStatus())) {
                backlog++;
            } else if (InstanceStatus.APPROVED.getCode().equals(i.getStatus())) {
                approved++;
                durations.add(hours(i.getCreatedAt(), i.getFinishedAt()));
            } else if (InstanceStatus.REJECTED.getCode().equals(i.getStatus())) {
                rejected++;
                durations.add(hours(i.getCreatedAt(), i.getFinishedAt()));
            } else if (InstanceStatus.WITHDRAWN.getCode().equals(i.getStatus())) {
                withdrawn++;
            } else if (InstanceStatus.TERMINATED.getCode().equals(i.getStatus())) {
                terminated++;
                durations.add(hours(i.getCreatedAt(), i.getFinishedAt()));
            }
        }
        vo.setTotal((long) list.size());
        vo.setApproved(approved);
        vo.setRejected(rejected);
        vo.setWithdrawn(withdrawn);
        vo.setTerminated(terminated);
        vo.setBacklog(backlog);
        vo.setAvgHours(durations.isEmpty() ? 0d : round2(durations.stream()
                .mapToDouble(Double::doubleValue).average().orElse(0)));
        vo.setP90Hours(p90(durations));
        long decided = approved + rejected;
        vo.setRejectRate(decided == 0 ? 0d : round2(rejected * 100.0 / decided));
        return vo;
    }

    /** 审批人维度统计：按 APPROVE 任务的处理时长聚合 */
    public List<ApprovalApproverStatsVO> approverStats(String appCode, String bizType,
                                                       Long orgId, LocalDate from, LocalDate to) {
        List<ApprInstance> instances = queryInstances(appCode, bizType, orgId, from, to, null);
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        List<Long> ids = instances.stream().map(ApprInstance::getId).collect(Collectors.toList());
        List<ApprTask> tasks = tasksOf(ids, List.of(TaskStatus.APPROVED.getCode(), TaskStatus.REJECTED.getCode()));
        Map<Long, Aggregate> byApprover = new LinkedHashMap<>();
        for (ApprTask t : tasks) {
            if (t.getApproverId() == null) {
                continue;
            }
            Aggregate a = byApprover.computeIfAbsent(t.getApproverId(), k -> new Aggregate());
            a.approverName = nullToEmpty(t.getApproverName());
            a.handled++;
            if (TaskStatus.APPROVED.getCode().equals(t.getStatus())) {
                a.approved++;
            } else {
                a.rejected++;
            }
            a.durations.add(hours(t.getCreatedAt(), t.getActedAt()));
        }
        List<ApprovalApproverStatsVO> out = new ArrayList<>();
        for (Map.Entry<Long, Aggregate> e : byApprover.entrySet()) {
            Aggregate a = e.getValue();
            ApprovalApproverStatsVO vo = new ApprovalApproverStatsVO();
            vo.setApproverId(e.getKey());
            vo.setApproverName(a.approverName);
            vo.setHandled(a.handled);
            vo.setApproved(a.approved);
            vo.setRejected(a.rejected);
            vo.setAvgHours(a.durations.isEmpty() ? 0d : round2(a.durations.stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0)));
            vo.setP90Hours(p90(a.durations));
            out.add(vo);
        }
        out.sort(Comparator.comparing(ApprovalApproverStatsVO::getHandled,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    /** 积压列表：在途单按当前节点等待时长倒序 */
    public List<ApprovalBacklogVO> backlog(String appCode, String bizType, Long orgId, int limit) {
        List<ApprInstance> instances = queryInstances(appCode, bizType, orgId, null, null,
                InstanceStatus.RUNNING.getCode());
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        if (instances.size() > limit) {
            instances = instances.subList(0, limit);
        }
        List<Long> ids = instances.stream().map(ApprInstance::getId).collect(Collectors.toList());
        Map<Long, List<ApprTask>> pendingByInstance = tasksOf(ids, List.of(TaskStatus.PENDING.getCode()))
                .stream().collect(Collectors.groupingBy(ApprTask::getInstanceId));
        LocalDateTime now = LocalDateTime.now();
        List<ApprovalBacklogVO> out = new ArrayList<>();
        for (ApprInstance inst : instances) {
            List<ApprTask> pending = pendingByInstance.getOrDefault(inst.getId(), List.of());
            if (pending.isEmpty()) {
                continue;
            }
            ApprovalSnapshot.Step step = currentStep(inst);
            LocalDateTime since = pending.stream().map(ApprTask::getCreatedAt)
                    .filter(x -> x != null).min(Comparator.naturalOrder())
                    .orElse(inst.getCreatedAt());
            ApprovalBacklogVO vo = new ApprovalBacklogVO();
            vo.setId(inst.getId());
            vo.setApprNo(inst.getApprNo());
            vo.setTitle(inst.getTitle());
            vo.setBizName(inst.getBizName());
            vo.setBizType(inst.getBizType());
            vo.setApplicantName(inst.getApplicantName());
            vo.setOrgPath(inst.getOrgPath());
            vo.setCurrentStep(inst.getCurrentStep());
            vo.setTotalStep(inst.getTotalStep());
            vo.setStepName(nullToEmpty(pending.get(0).getStepName()));
            vo.setSubmittedAt(inst.getCreatedAt());
            vo.setWaitingSince(since);
            vo.setWaitingHours(round2(hours(since, now)));
            vo.setSlaHours(step == null ? null : step.getSlaHours());
            vo.setOverdue(step != null && step.getSlaHours() != null && step.getSlaHours() > 0
                    && vo.getWaitingHours() > step.getSlaHours());
            vo.setApproverNames(pending.stream().map(ApprTask::getApproverName)
                    .filter(x -> x != null && !x.isBlank()).distinct().collect(Collectors.joining("、")));
            vo.setUrgeCount(inst.getUrgeCount() == null ? 0 : inst.getUrgeCount());
            vo.setLastUrgeAt(inst.getLastUrgeAt());
            out.add(vo);
        }
        out.sort(Comparator.comparing(ApprovalBacklogVO::getWaitingHours,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    // ================================================================ 批量审批

    /**
     * 批量通过/驳回。逐条校验（任务存在、属于操作人、实例进行中），
     * 单条失败记入 failed；已成功的单已各自提交，不回滚。
     */
    public BatchApprovalResultVO batch(BatchApprovalRequest req) {
        BatchApprovalResultVO result = new BatchApprovalResultVO();
        if (req == null || req.getTaskIds() == null || req.getTaskIds().isEmpty()) {
            return result;
        }
        boolean pass = !"REJECT".equalsIgnoreCase(req.getAction());
        int limit = 200;
        int done = 0;
        for (Long taskId : req.getTaskIds()) {
            if (done >= limit) {
                result.addFailure(taskId, "单次最多批量处理 " + limit + " 条");
                continue;
            }
            done++;
            if (taskId == null) {
                result.addFailure(null, "任务 ID 为空");
                continue;
            }
            try {
                ApprovalActionRequest action = new ApprovalActionRequest();
                action.setOperatorId(req.getOperatorId());
                action.setOperatorName(req.getOperatorName());
                action.setPass(pass);
                action.setNote(req.getComment());
                engine.approve(taskId, action);
                result.addSuccess(taskId);
            } catch (Exception e) {
                log.warn("批量审批失败：taskId={} err={}", taskId, e.getMessage());
                result.addFailure(taskId, e.getMessage() == null ? "处理失败" : e.getMessage());
            }
        }
        return result;
    }

    // ================================================================ 导出

    /**
     * 导出 CSV（带 BOM，Excel 直接打开不乱码）。
     * type=pending 只导当前操作人的待办；type=instances 按筛选条件导审批流水。
     * 只导出审批元数据，不含 form_data 等业务明文；导出行为写操作日志留痕。
     */
    public String export(String type, Long accountId, String appCode, String bizType,
                         Long orgId, LocalDate from, LocalDate to, Long operatorId) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        int rows;
        if ("pending".equalsIgnoreCase(type)) {
            if (accountId == null) {
                throw new BizException(SettingsErrorCode.APPROVAL_INSTANCE_NOT_FOUND, "导出待办需要登录身份");
            }
            appendLine(sb, "审批单号", "审批类型", "标题", "发起人", "所属组织", "当前步骤",
                    "提交时间", "已等待小时", "我的任务ID");
            List<ApprTask> mine = taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                    .eq(ApprTask::getApproverId, accountId)
                    .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode())
                    .eq(ApprTask::getTaskKind, "APPROVE")
                    .orderByAsc(ApprTask::getCreatedAt));
            Map<Long, ApprInstance> byId = instancesById(mine.stream()
                    .map(ApprTask::getInstanceId).collect(Collectors.toList()));
            LocalDateTime now = LocalDateTime.now();
            rows = 0;
            for (ApprTask t : mine) {
                ApprInstance inst = byId.get(t.getInstanceId());
                if (inst == null) {
                    continue;
                }
                appendLine(sb, inst.getApprNo(), inst.getBizName(), inst.getTitle(),
                        inst.getApplicantName(), inst.getOrgPath(), t.getStepName(),
                        fmt(inst.getCreatedAt()), String.valueOf(round2(hours(t.getCreatedAt(), now))),
                        String.valueOf(t.getId()));
                rows++;
            }
        } else {
            appendLine(sb, "审批单号", "审批类型", "标题", "发起人", "所属组织", "状态",
                    "当前步骤", "提交时间", "结束时间", "耗时小时", "催办次数");
            List<ApprInstance> list = queryInstances(appCode, bizType, orgId, from, to, null);
            rows = 0;
            if (list != null) {
                for (ApprInstance inst : list) {
                    InstanceStatus st = InstanceStatus.of(inst.getStatus());
                    appendLine(sb, inst.getApprNo(), inst.getBizName(), inst.getTitle(),
                            inst.getApplicantName(), inst.getOrgPath(), st == null ? "" : st.getLabel(),
                            nvl(inst.getCurrentStep()) + "/" + nvl(inst.getTotalStep()),
                            fmt(inst.getCreatedAt()), fmt(inst.getFinishedAt()),
                            String.valueOf(round2(hours(inst.getCreatedAt(), inst.getFinishedAt()))),
                            String.valueOf(inst.getUrgeCount() == null ? 0 : inst.getUrgeCount()));
                    rows++;
                }
            }
        }
        log.info("审批导出审计：operatorId={} type={} rows={}", operatorId, type, rows);
        return sb.toString();
    }

    // ================================================================ 工具

    /**
     * 统一筛选。返回 null 表示「appCode 下没有任何模板」——调用方直接按空结果处理，
     * 避免拼出 IN () 这种非法 SQL。
     */
    private List<ApprInstance> queryInstances(String appCode, String bizType, Long orgId,
                                              LocalDate from, LocalDate to, Integer status) {
        LambdaQueryWrapper<ApprInstance> w = Wrappers.lambdaQuery();
        if (appCode != null && !appCode.isBlank()) {
            List<Long> templateIds = templateMapper.selectList(Wrappers.<ApprovalTemplate>lambdaQuery()
                            .eq(ApprovalTemplate::getAppCode, appCode.trim()))
                    .stream().map(ApprovalTemplate::getId).collect(Collectors.toList());
            if (templateIds.isEmpty()) {
                return null;
            }
            w.in(ApprInstance::getTemplateId, templateIds);
        }
        if (bizType != null && !bizType.isBlank()) {
            w.eq(ApprInstance::getBizType, bizType.trim());
        }
        if (orgId != null) {
            w.eq(ApprInstance::getApplicantOrgId, orgId);
        }
        if (status != null) {
            w.eq(ApprInstance::getStatus, status);
        }
        if (from != null) {
            w.ge(ApprInstance::getCreatedAt, from.atStartOfDay());
        }
        if (to != null) {
            w.lt(ApprInstance::getCreatedAt, to.plusDays(1).atStartOfDay());
        }
        return instanceMapper.selectList(w.orderByAsc(ApprInstance::getCreatedAt));
    }

    private List<ApprTask> tasksOf(List<Long> instanceIds, List<Integer> statuses) {
        List<ApprTask> out = new ArrayList<>();
        for (int i = 0; i < instanceIds.size(); i += CHUNK) {
            List<Long> part = instanceIds.subList(i, Math.min(i + CHUNK, instanceIds.size()));
            out.addAll(taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                    .in(ApprTask::getInstanceId, part)
                    .eq(ApprTask::getTaskKind, "APPROVE")
                    .in(ApprTask::getStatus, statuses)
                    .orderByAsc(ApprTask::getCreatedAt)));
        }
        return out;
    }

    private Map<Long, ApprInstance> instancesById(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ApprInstance> list = instanceMapper.selectBatchIds(ids.stream().distinct().collect(Collectors.toList()));
        return list.stream().collect(Collectors.toMap(ApprInstance::getId, x -> x, (a, b) -> a));
    }

    /** 实例快照里的当前步骤；快照损坏或旧单缺字段时返回 null */
    public ApprovalSnapshot.Step currentStep(ApprInstance inst) {
        if (inst == null || inst.getSnapshot() == null || inst.getSnapshot().isBlank()) {
            return null;
        }
        try {
            ApprovalSnapshot snap = objectMapper.readValue(inst.getSnapshot(), ApprovalSnapshot.class);
            Integer seq = inst.getCurrentStep();
            for (ApprovalSnapshot.Step s : snap.getSteps()) {
                if (seq != null && seq.equals(s.getSeq())) {
                    return s;
                }
            }
        } catch (Exception e) {
            log.warn("审批快照解析失败，跳过时效判断：instance={}", inst.getId());
        }
        return null;
    }

    private List<ApprTask> pendingTasks(Long instanceId) {
        return taskMapper.selectList(Wrappers.<ApprTask>lambdaQuery()
                .eq(ApprTask::getInstanceId, instanceId)
                .eq(ApprTask::getTaskKind, "APPROVE")
                .eq(ApprTask::getStatus, TaskStatus.PENDING.getCode())
                .orderByAsc(ApprTask::getCreatedAt));
    }

    private void notifyApprovers(ApprInstance inst, List<ApprTask> pending, String ruleCode,
                                 String title, String content) {
        for (ApprTask t : pending) {
            if (t.getApproverId() == null) {
                continue;
            }
            try {
                NoticeSendRequest req = new NoticeSendRequest();
                req.setRuleCode(ruleCode);
                req.setAccountId(t.getApproverId());
                // 超时/催办是「提醒」不是新待办，固定走站内消息，不重复造待办
                req.setChannel(2);
                req.setBizType(inst.getBizType());
                req.setBizId(String.valueOf(inst.getId()));
                req.setBizNo(nullToEmpty(inst.getApprNo()));
                req.setApplicantName(nullToEmpty(inst.getApplicantName()));
                req.setLink("/approvals");
                req.setTitle(title);
                req.setContent(content);
                req.getVars().put("bizName", nullToEmpty(inst.getBizName()));
                req.getVars().put("title", nullToEmpty(inst.getTitle()));
                req.getVars().put("bizNo", nullToEmpty(inst.getApprNo()));
                req.getVars().put("applicantName", nullToEmpty(inst.getApplicantName()));
                req.getVars().put("stepName", nullToEmpty(t.getStepName()));
                noticeService.send(req);
            } catch (Exception e) {
                log.warn("审批提醒发送失败：instance={} approver={} err={}",
                        inst.getId(), t.getApproverId(), e.getMessage());
            }
        }
    }

    private static double hours(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0d;
        }
        return Math.max(0, Duration.between(from, to).toMinutes()) / 60.0;
    }

    private static double p90(List<Double> values) {
        if (values.isEmpty()) {
            return 0d;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int idx = (int) Math.ceil(sorted.size() * 0.9) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return round2(sorted.get(idx));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static void appendLine(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csv(cells[i]));
        }
        sb.append('\n');
    }

    /** CSV 转义：含逗号/引号/换行的值加双引号，内部引号翻倍 */
    private static String csv(String v) {
        String s = v == null ? "" : v;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String fmt(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    private static String nvl(Integer v) {
        return v == null ? "0" : String.valueOf(v);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 审批人聚合中间体 */
    private static class Aggregate {
        private String approverName = "";
        private long handled;
        private long approved;
        private long rejected;
        private final List<Double> durations = new ArrayList<>();
    }
}

package com.laimengchao.sso.settings.module.approval.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.approval.dto.ApprovalActionRequest;
import com.laimengchao.sso.settings.module.approval.dto.ApprovalDelegateRequest;
import com.laimengchao.sso.settings.module.approval.dto.BatchApprovalRequest;
import com.laimengchao.sso.settings.module.approval.dto.StartApprovalRequest;
import com.laimengchao.sso.settings.module.approval.constant.InstanceStatus;
import com.laimengchao.sso.settings.module.approval.entity.ApprDelegate;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalPage;
import com.laimengchao.sso.settings.module.approval.entity.BizScene;
import com.laimengchao.sso.settings.module.approval.mapper.ApprInstanceMapper;
import com.laimengchao.sso.settings.module.approval.mapper.BizSceneMapper;
import com.laimengchao.sso.settings.module.approval.service.ApprovalConfigService;
import com.laimengchao.sso.settings.module.approval.service.ApprovalContextService;
import com.laimengchao.sso.settings.module.approval.service.ApprovalCallbackService;
import com.laimengchao.sso.settings.module.approval.service.ApprovalEngine;
import com.laimengchao.sso.settings.module.approval.service.ApprovalMoveService;
import com.laimengchao.sso.settings.module.approval.service.ApprovalOpsService;
import com.laimengchao.sso.settings.module.approval.service.ApprovalQueryService;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalApproverStatsVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalBacklogVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalContextVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalInstanceVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalStatsVO;
import com.laimengchao.sso.settings.module.approval.vo.BatchApprovalResultVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批内部接口：供业务服务（ehr 等）与统一账号中心（审批中心）调用。
 * <p>
 * 走 {@code /internal/approval/**}，凭 {@code X-Internal-Token} 准入（见 InternalTokenInterceptor），
 * 不经网关。操作人身份由调用方从令牌解析后放进请求体，本服务不解析 JWT——
 * 它没有业务应用的密钥，也不该有。
 */
@RestController
@RequestMapping("/internal/approval")
@RequiredArgsConstructor
public class InternalApprovalController {

    private final ApprovalEngine engine;
    private final ApprovalQueryService queryService;
    private final ApprovalMoveService moveService;
    private final ApprovalOpsService opsService;
    private final ApprovalConfigService configService;
    private final ApprovalContextService contextService;
    private final ApprovalCallbackService callbackService;
    private final ApprInstanceMapper instanceMapper;
    private final BizSceneMapper sceneMapper;
    private final com.laimengchao.sso.settings.module.approval.service.InternalHttpClient http;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${approval.ehr-base-url:http://127.0.0.1:9006}")
    private String ehrBaseUrl;

    /** JSON_EXTRACT 的路径字段只允许标识符字符，防止把场景配置直接拼进 SQL */
    private static final java.util.regex.Pattern SAFE_JSON_FIELD =
            java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    /** 发起审批 */
    @PostMapping("/start")
    public R<ApprovalInstanceVO> start(@RequestBody StartApprovalRequest req) {
        ApprInstance inst = engine.start(req);
        return R.ok(queryService.detail(inst.getId(), req.getApplicantId()));
    }

    /** 登记审批委托（部门负责人请假期间代审）；ownerId + remark 幂等，重复回调只更新 */
    @PostMapping("/delegates")
    public R<Long> delegate(@RequestBody ApprovalDelegateRequest req) {
        ApprDelegate d = new ApprDelegate();
        d.setOwnerId(req.getOwnerId());
        d.setOwnerName(req.getOwnerName());
        d.setDelegateId(req.getDelegateId());
        d.setDelegateName(req.getDelegateName());
        d.setBizType(req.getBizType());
        d.setStartTime(req.getStartTime());
        d.setEndTime(req.getEndTime());
        d.setRemark(req.getRemark());
        return R.ok(moveService.upsertDelegate(d).getId());
    }

    /** 某笔业务的最新审批单（业务方判断「能不能再次发起」「上次结果是什么」） */
    @GetMapping("/instances/latest")
    public R<ApprovalInstanceVO> latest(@RequestParam String bizType, @RequestParam String bizId) {
        ApprInstance inst = instanceMapper.selectOne(Wrappers.<ApprInstance>lambdaQuery()
                .eq(ApprInstance::getBizType, bizType)
                .eq(ApprInstance::getBizId, bizId)
                // 按 id 而不是 created_at：datetime 只到秒，同一秒内先后发起的两张单分不出新旧，
                // 雪花 ID 单调递增，永远能拿到真正最新的那张
                .orderByDesc(ApprInstance::getId)
                .last("limit 1"));
        return R.ok(inst == null ? null : queryService.toVO(inst, null));
    }

    /**
     * 批量查「某笔业务的最新审批单」：业务列表在无在途单时要显示「XX审批被驳回」并给出详情入口。
     * <p>
     * <b>为什么要批量</b>：业务回调在终态会清掉业务表上的实例号（避免列表一直显示「审批中」），
     * 所以驳回提示只能反查审批单；若每行打一次 {@code /instances/latest}，一页就是几十次调用。
     * <p>
     * <b>为什么返回精简 Map</b>：{@code ApprovalInstanceVO} 带 steps/tasks，批量返回太重；
     * 雪花 id 必须按字符串给，前端 JS 保不住 19 位精度。
     */
    @PostMapping("/instances/latest-batch")
    public R<List<Map<String, Object>>> latestBatch(@RequestBody(required = false) List<BizRef> refs) {
        Map<String, Set<String>> byType = new LinkedHashMap<>();
        // bySubject=1：ref 里的 bizId 实际是「业务主体 ID」（员工 / 组织），按主体反查单据。
        // 主体键来自 cfg_biz_scene.subject_field，员工、组织、新增业务共用这一条路径。
        Map<String, Set<String>> bySubjectType = new LinkedHashMap<>();
        Set<String> bizIds = new LinkedHashSet<>();
        Set<String> subjectIds = new LinkedHashSet<>();
        int refCount = 0;
        if (refs != null) {
            for (BizRef r : refs) {
                if (r == null || r.getBizType() == null || r.getBizType().isBlank()
                        || r.getBizId() == null || r.getBizId().isBlank()) {
                    continue;
                }
                String type = r.getBizType().trim();
                String id = r.getBizId().trim();
                if (r.wantsSubjectLookup()) {
                    bySubjectType.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(id);
                    subjectIds.add(id);
                } else {
                    byType.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(id);
                    bizIds.add(id);
                }
                // 内部接口也要挡一下异常入参，避免一次拉爆整张表
                if (byType.size() + bySubjectType.size() > 32
                        || bizIds.size() + subjectIds.size() > 500 || ++refCount > 1000) {
                    break;
                }
            }
        }
        if (byType.isEmpty() && bySubjectType.isEmpty()) {
            return R.ok(List.of());
        }

        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();

        // 1) 精确匹配：bizId 就是业务单 ID（请假单 / 转正申请单 / 离职申请单等列表）
        if (!byType.isEmpty()) {
            List<ApprInstance> list = instanceMapper.selectList(Wrappers.<ApprInstance>lambdaQuery()
                    .in(ApprInstance::getBizType, byType.keySet())
                    .in(ApprInstance::getBizId, bizIds)
                    .orderByDesc(ApprInstance::getId));
            for (ApprInstance inst : list) {
                Set<String> wanted = byType.get(inst.getBizType());
                if (wanted == null || !wanted.contains(inst.getBizId())) {
                    continue;
                }
                addBrief(out, seen, inst, inst.getBizId());
            }
        }

        // 2) 按业务主体反查：新单命中 subject_id 列，存量单回落 form_data 的 JSON_EXTRACT。
        //    对外把 bizId 改写成主体 ID，人员/组织列表才能按行归类显示「审批中 / 被驳回」。
        if (!bySubjectType.isEmpty()) {
            for (Map.Entry<String, Set<String>> e : bySubjectType.entrySet()) {
                String field = subjectFieldOf(e.getKey());
                if (field == null) {
                    continue;
                }
                for (ApprInstance inst : findBySubject(e.getKey(), field, e.getValue())) {
                    String sid = subjectIdOf(inst, field);
                    if (sid == null || !e.getValue().contains(sid)) {
                        continue;
                    }
                    addBrief(out, seen, inst, sid);
                }
            }
        }
        return R.ok(out);
    }

    /** 某业务主体是否有在途单；业务侧「能否再次发起」统一走这里，不再读业务表的单值实例号 */
    @GetMapping("/instances/running")
    public R<Boolean> running(@RequestParam String subjectId,
                              @RequestParam(required = false) String bizType) {
        Long n = instanceMapper.selectCount(Wrappers.<ApprInstance>lambdaQuery()
                .eq(ApprInstance::getSubjectId, subjectId)
                .eq(ApprInstance::getStatus, InstanceStatus.RUNNING.getCode())
                .eq(bizType != null && !bizType.isBlank(), ApprInstance::getBizType, bizType));
        return R.ok(n != null && n > 0);
    }

    /**
     * 场景配置里的主体键（{@code cfg_biz_scene.subject_field}）。
     * 老场景没配时按 appCode 兜底；字段名必须过标识符白名单才允许拼进 SQL。
     */
    private String subjectFieldOf(String bizType) {
        BizScene scene = sceneMapper.selectOne(Wrappers.<BizScene>lambdaQuery()
                .eq(BizScene::getBizType, bizType).last("limit 1"));
        if (scene != null && scene.getSubjectField() != null && !scene.getSubjectField().isBlank()) {
            String f = scene.getSubjectField().trim();
            return SAFE_JSON_FIELD.matcher(f).matches() ? f : null;
        }
        String app = scene == null ? null : scene.getAppCode();
        if (app == null || app.isBlank()) {
            return "employeeId";
        }
        return switch (app.trim().toLowerCase()) {
            case "ehr" -> "employeeId";
            case "cms", "blog" -> "articleId";
            default -> "id";
        };
    }

    /**
     * 主体反查一次覆盖新旧两类单：新单有 subject_id 列，补列前的存量单只有冻结表单。
     * 两路参数各一份，按 MP {@code .apply()} 的 {i} 占位顺序展开。
     */
    private List<ApprInstance> findBySubject(String bizType, String field, Set<String> wanted) {
        Object[] ids = wanted.toArray();
        StringBuilder now = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                now.append(',');
            }
            now.append('{').append(i).append('}');
        }
        StringBuilder legacy = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                legacy.append(',');
            }
            legacy.append('{').append(ids.length + i).append('}');
        }
        Object[] params = new Object[ids.length * 2];
        System.arraycopy(ids, 0, params, 0, ids.length);
        System.arraycopy(ids, 0, params, ids.length, ids.length);
        String extract = "JSON_UNQUOTE(JSON_EXTRACT(form_data, '$." + field + "'))";
        return instanceMapper.selectList(Wrappers.<ApprInstance>lambdaQuery()
                .eq(ApprInstance::getBizType, bizType)
                .apply("(subject_id IN (" + now + ") OR (subject_id IS NULL AND "
                        + extract + " IN (" + legacy + ")))", params)
                .orderByDesc(ApprInstance::getId));
    }

    /** 按 (bizType, 对外 bizId) 去重；查询结果已按雪花 id 倒序，第一次出现即最新一张 */
    private void addBrief(List<Map<String, Object>> out, Set<String> seen,
                          ApprInstance inst, String outBizId) {
        if (!seen.add(inst.getBizType() + "|" + outBizId)) {
            return;
        }
        InstanceStatus st = InstanceStatus.of(inst.getStatus());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(inst.getId()));
        m.put("bizType", inst.getBizType());
        m.put("bizId", outBizId);
        m.put("subjectId", inst.getSubjectId());
        m.put("bizIdKind", inst.getBizIdKind());
        m.put("status", inst.getStatus());
        m.put("statusName", st == null ? "" : st.getLabel());
        m.put("title", inst.getTitle());
        m.put("finishedAt", inst.getFinishedAt() == null ? null : inst.getFinishedAt().toString());
        out.add(m);
    }

    /** 新单读 subject_id 列；存量单从冻结表单按场景配置的主体键取，异常返回 null */
    private String subjectIdOf(ApprInstance inst, String field) {
        if (inst.getSubjectId() != null && !inst.getSubjectId().isBlank()) {
            return inst.getSubjectId();
        }
        String fd = inst.getFormData();
        if (fd == null || fd.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode n = objectMapper.readTree(fd);
            com.fasterxml.jackson.databind.JsonNode v = n == null ? null : n.get(field);
            return v == null || v.isNull() ? null : v.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /** 批量反查的请求项；单独声明而不是收 Map，前端字段名写错时会直接被忽略而不是静默查不到 */
    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class BizRef {
        private String bizType;
        private String bizId;
        /** "1"/"true"：bizId 是业务主体 ID，按 cfg_biz_scene.subject_field 反查（员工/组织通用） */
        private String bySubject;
        /** 兼容旧前端：按员工 ID 反查 */
        private String byEmployee;
        /** 兼容旧前端：按组织 ID 反查 */
        private String byOrg;

        /** 不用 isXxx 命名：避免 Jackson 把它当成 bySubject 属性的 getter 影响反序列化 */
        public boolean wantsSubjectLookup() {
            return flag(bySubject) || flag(byEmployee) || flag(byOrg);
        }

        private static boolean flag(String v) {
            return "1".equals(v) || "true".equalsIgnoreCase(v);
        }
    }

    @GetMapping("/instances/{id}")
    public R<ApprovalInstanceVO> detail(@PathVariable Long id,
                                        @RequestParam(required = false) Long viewerId) {
        return R.ok(queryService.detail(id, viewerId));
    }

    /** 待办五视图：pending 待我审批 / approved 已审批 / mine 我发起 / cc 抄送我 / delegated 我委托的 */
    @GetMapping("/pending")
    public R<List<ApprovalInstanceVO>> pending(@RequestParam Long accountId,
                                               @RequestParam(defaultValue = "pending") String scope) {
        return R.ok(queryService.list(accountId, scope));
    }

    @PostMapping("/tasks/{taskId}/approve")
    public R<ApprovalInstanceVO> approve(@PathVariable Long taskId,
                                         @RequestBody ApprovalActionRequest req) {
        ApprInstance inst = engine.approve(taskId, req);
        return R.ok(queryService.detail(inst.getId(), req.getOperatorId()));
    }

    /** 批量通过/驳回：逐条独立事务，部分失败不回滚 */
    @PostMapping("/tasks/batch")
    public R<BatchApprovalResultVO> batch(@RequestBody BatchApprovalRequest req) {
        return R.ok(opsService.batch(req));
    }

    // ---------------------------------------------------------------- 运营

    /** 时效总览（按应用/场景/组织/日期过滤） */
    @GetMapping("/stats")
    public R<ApprovalStatsVO> stats(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to) {
        return R.ok(opsService.stats(appCode, bizType, orgId, from, to));
    }

    /** 审批人维度时效 */
    @GetMapping("/stats/approver")
    public R<List<ApprovalApproverStatsVO>> approverStats(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate from,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate to) {
        return R.ok(opsService.approverStats(appCode, bizType, orgId, from, to));
    }

    /** 积压列表 */
    @GetMapping("/backlog")
    public R<List<ApprovalBacklogVO>> backlog(@RequestParam(required = false) String appCode,
                                              @RequestParam(required = false) String bizType,
                                              @RequestParam(required = false) Long orgId,
                                              @RequestParam(defaultValue = "100") int limit) {
        return R.ok(opsService.backlog(appCode, bizType, orgId, Math.min(Math.max(limit, 1), 200)));
    }

    /** 催办：写 appr_action(action=URGE) 并给当前待办人发站内提醒 */
    @PostMapping("/instances/{id}/urge")
    public R<ApprInstance> urge(@PathVariable Long id,
                                @RequestBody(required = false) ApprovalActionRequest req) {
        return R.ok(opsService.urge(id, req == null ? new ApprovalActionRequest() : req));
    }

    /** 导出 CSV：type=pending 需传 accountId；type=instances 按筛选条件 */
    @GetMapping("/export")
    public R<String> export(@RequestParam(defaultValue = "pending") String type,
                            @RequestParam(required = false) Long accountId,
                            @RequestParam(required = false) String appCode,
                            @RequestParam(required = false) String bizType,
                            @RequestParam(required = false) Long orgId,
                            @RequestParam(required = false)
                            @org.springframework.format.annotation.DateTimeFormat(
                                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                            java.time.LocalDate from,
                            @RequestParam(required = false)
                            @org.springframework.format.annotation.DateTimeFormat(
                                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                            java.time.LocalDate to,
                            @RequestParam(required = false) Long operatorId) {
        return R.ok(opsService.export(type, accountId, appCode, bizType, orgId, from, to, operatorId));
    }

    @PostMapping("/tasks/{taskId}/transfer")
    public R<Void> transfer(@PathVariable Long taskId, @RequestBody ApprovalActionRequest req) {
        moveService.transfer(taskId, req);
        return R.ok();
    }

    @PostMapping("/tasks/{taskId}/add-sign")
    public R<Void> addSign(@PathVariable Long taskId, @RequestBody ApprovalActionRequest req) {
        moveService.addSign(taskId, req);
        return R.ok();
    }

    @PostMapping("/instances/{id}/cc")
    public R<Void> cc(@PathVariable Long id,
                      @RequestParam List<Long> accountIds,
                      @RequestBody ApprovalActionRequest req) {
        moveService.cc(id, accountIds, req);
        return R.ok();
    }

    @PostMapping("/instances/{id}/withdraw")
    public R<ApprovalInstanceVO> withdraw(@PathVariable Long id, @RequestBody ApprovalActionRequest req) {
        ApprInstance inst = engine.withdraw(id, req);
        return R.ok(queryService.detail(inst.getId(), req.getOperatorId()));
    }

    @PostMapping("/instances/{id}/terminate")
    public R<ApprovalInstanceVO> terminate(@PathVariable Long id, @RequestBody ApprovalActionRequest req) {
        ApprInstance inst = engine.terminate(id, req);
        return R.ok(queryService.detail(inst.getId(), req.getOperatorId()));
    }

    /** 终审回调失败的，人工重试一次 */
    @PostMapping("/instances/{id}/retry-callback")
    public R<Void> retryCallback(@PathVariable Long id) {
        callbackService.retryNow(id);
        return R.ok();
    }

    /* ---------------- 选人辅助：转办 / 加签按组织树选人 ---------------- */

    /**
     * 全量组织树（扁平，前端组树）。转办 / 加签的选人弹窗用：
     * 账号中心（审批中心）没有组织概念，组织与员工的真相源在 EHR，
     * 由本服务用内部通道转一手，账号中心再转给浏览器——
     * 链路是 浏览器 → account(用户令牌) → settings(内部令牌) → ehr(内部令牌)。
     */
    @GetMapping("/orgs/tree")
    public R<com.fasterxml.jackson.databind.JsonNode> orgTree() {
        return R.ok(http.getData(ehrBaseUrl + "/internal/orgs/tree"));
    }

    /** 某组织直属成员（已开户、未离职），转办 / 加签候选人 */
    @GetMapping("/orgs/{orgId}/employees")
    public R<com.fasterxml.jackson.databind.JsonNode> orgEmployees(@PathVariable Long orgId) {
        return R.ok(http.getData(ehrBaseUrl + "/internal/orgs/" + orgId + "/employees"));
    }

    /** 审批页面配置：业务侧渲染审批弹窗时取 */
    @GetMapping("/pages/{bizType}")
    public R<ApprovalPage> page(@PathVariable String bizType,
                                @RequestParam(defaultValue = "APPROVAL") String view) {
        return R.ok(configService.getPageByBizType(bizType, view));
    }

    /**
     * 审批弹窗的渲染上下文：页面配置 + 已算好的字段值。
     * <p>
     * 与 {@link #page} 的区别：page 只给版式，调用方还得自己去业务库拼值；
     * context 把值也算完了一起返回，前端拿到直接铺开——数据口径只在服务端一处。
     */
    @GetMapping("/instances/{id}/context")
    public R<ApprovalContextVO> context(@PathVariable Long id,
                                        @RequestParam(required = false) Long viewerId,
                                        @RequestParam(defaultValue = "APPROVAL") String view) {
        return R.ok(contextService.context(id, viewerId, view));
    }
}

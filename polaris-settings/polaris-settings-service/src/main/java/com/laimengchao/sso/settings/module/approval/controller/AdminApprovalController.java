package com.laimengchao.sso.settings.module.approval.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.constant.PermissionCodes;
import com.laimengchao.sso.settings.module.approval.dto.ApprovalActionRequest;
import com.laimengchao.sso.settings.module.approval.dto.BatchApprovalRequest;
import com.laimengchao.sso.settings.module.approval.dto.BizSceneVO;
import com.laimengchao.sso.settings.module.approval.dto.SceneTableBindingsDto;
import com.laimengchao.sso.settings.module.approval.dto.TemplateDetailVO;
import com.laimengchao.sso.settings.module.approval.dto.TemplateSaveRequest;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalPage;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalTemplate;
import com.laimengchao.sso.settings.module.approval.entity.BizScene;
import com.laimengchao.sso.settings.module.approval.mapper.ApprovalTemplateMapper;
import com.laimengchao.sso.settings.module.approval.service.ApprovalConfigService;
import com.laimengchao.sso.settings.module.approval.service.BizPointReferenceService;
import com.laimengchao.sso.settings.module.approval.service.ApprovalOpsService;
import com.laimengchao.sso.settings.module.approval.service.ConditionEvaluator;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalApproverStatsVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalBacklogVO;
import com.laimengchao.sso.settings.module.approval.vo.ApprovalStatsVO;
import com.laimengchao.sso.settings.module.approval.vo.BatchApprovalResultVO;
import com.laimengchao.sso.settings.security.Permit;
import com.laimengchao.sso.settings.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批配置管理端：模板、步骤、分支、场景、页面。
 * <p>
 * 走网关 {@code /api/settings/approval/**}，令牌必须是 settings 作用域（见 PermissionAspect）。
 * 注意与「审批中心」的区别：这里是<b>配流程</b>的（管理员），审批中心是<b>办单子</b>的（审批人），
 * 后者在统一账号中心，通过 sso-account 代理调本服务的 /internal/approval/**。
 */
@RestController
@RequestMapping("/api/settings/approval")
@RequiredArgsConstructor
public class AdminApprovalController {

    private final ApprovalConfigService configService;
    private final BizPointReferenceService bizPointReferences;
    private final ConditionEvaluator conditionEvaluator;
    private final com.laimengchao.sso.settings.module.approval.service.InternalHttpClient internalHttpClient;
    private final com.laimengchao.sso.settings.module.approval.service.EntityDataClient entityDataClient;
    private final ApprovalTemplateMapper templateMapper;
    private final ApprovalOpsService opsService;

    /** 组织数据在 EHR，这里只做代理（前端令牌是 settings 作用域，直连会被 EHR 拒） */
    @org.springframework.beans.factory.annotation.Value("${approval.ehr-base-url:http://127.0.0.1:9006}")
    private String ehrBaseUrl;

    /** 应用注册表在统一账号中心 */
    @org.springframework.beans.factory.annotation.Value("${approval.account-base-url:http://127.0.0.1:9002}")
    private String accountBaseUrl;

    /** 启用中的应用清单，「所属应用」下拉用（转自 account /internal/apps，只含 id/code/name） */
    @GetMapping("/apps")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<com.fasterxml.jackson.databind.JsonNode> apps() {
        return R.ok(internalHttpClient.getData(accountBaseUrl + "/internal/apps"));
    }

    /**
     * 全量角色清单，「角色」审批人规则下拉用（转自 account /internal/roles）。
     * 不按应用过滤——角色归属哪个应用只是管理分组，审批人配置关心的是「谁能审」。
     */
    @GetMapping("/roles")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<com.fasterxml.jackson.databind.JsonNode> roles() {
        return R.ok(internalHttpClient.getData(accountBaseUrl + "/internal/roles"));
    }

    /** 组织树（扁平），给「指定账号」规则选人用 */
    @GetMapping("/orgs/tree")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<com.fasterxml.jackson.databind.JsonNode> orgTree() {
        return R.ok(internalHttpClient.getData(ehrBaseUrl + "/internal/orgs/tree"));
    }

    /** 组织直属成员（已开户未离职），审批人候选 */
    @GetMapping("/orgs/{orgId}/employees")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<com.fasterxml.jackson.databind.JsonNode> orgEmployees(@PathVariable Long orgId) {
        return R.ok(internalHttpClient.getData(ehrBaseUrl + "/internal/orgs/" + orgId + "/employees"));
    }

    @GetMapping("/templates")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<List<TemplateDetailVO>> list() {
        return R.ok(configService.list());
    }

    @GetMapping("/templates/{id}")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<TemplateDetailVO> detail(@PathVariable Long id) {
        return R.ok(configService.detail(id));
    }

    @PostMapping("/templates")
    @Permit(PermissionCodes.APPROVAL_MANAGE)
    public R<TemplateDetailVO> create(@RequestBody TemplateSaveRequest req) {
        req.setId(null);
        return R.ok(configService.save(req));
    }

    @PutMapping("/templates/{id}")
    @Permit(PermissionCodes.APPROVAL_MANAGE)
    public R<TemplateDetailVO> update(@PathVariable Long id, @RequestBody TemplateSaveRequest req) {
        req.setId(id);
        return R.ok(configService.save(req));
    }

    @PatchMapping("/templates/{id}/status")
    @Permit(PermissionCodes.APPROVAL_MANAGE)
    public R<Void> toggle(@PathVariable Long id, @RequestParam Integer enabled) {
        configService.toggle(id, enabled);
        return R.ok();
    }

    @DeleteMapping("/templates/{id}")
    @Permit(PermissionCodes.APPROVAL_MANAGE)
    public R<Void> remove(@PathVariable Long id) {
        configService.remove(id);
        return R.ok();
    }

    // ---------------------------------------------------------------- 页面

    @GetMapping("/templates/{id}/page")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<ApprovalPage> page(@PathVariable Long id,
                                @RequestParam(defaultValue = "APPROVAL") String view) {
        return R.ok(configService.getPage(id, view));
    }

    @PutMapping("/templates/{id}/page")
    @Permit(PermissionCodes.APPROVAL_MANAGE)
    public R<ApprovalPage> savePage(@PathVariable Long id,
                                    @RequestBody TemplateSaveRequest.PageDto dto) {
        return R.ok(configService.savePage(id, dto));
    }

    // ---------------------------------------------------------------- 场景

    /**
     * 业务场景列表。场景本身不存模板：绑定关系反向存在模板表的 biz_type 上，
     * 这里按 bizType 关联一次，前端才能显示「绑了哪个模板 / 是否启用」。
     * <p>
     * 直接查模板表而不是调 {@code configService.list()}：后者会顺带统计步骤数、
     * 分支数、在途单数（每个模板一条 count），为了两个展示字段不值得。
     */
    @GetMapping("/scenes")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<List<BizSceneVO>> scenes() {
        Map<String, ApprovalTemplate> tplByBizType = new LinkedHashMap<>();
        for (ApprovalTemplate t : templateMapper.selectList(Wrappers.<ApprovalTemplate>lambdaQuery()
                .orderByDesc(ApprovalTemplate::getVersion).orderByDesc(ApprovalTemplate::getId))) {
            // 严格一对一，理论上一场景一条；万一历史数据有多条，取版本最高的那条
            tplByBizType.putIfAbsent(t.getBizType(), t);
        }
        List<BizSceneVO> out = new ArrayList<>();
        for (BizScene s : configService.scenes()) {
            BizSceneVO vo = new BizSceneVO();
            org.springframework.beans.BeanUtils.copyProperties(s, vo);
            ApprovalTemplate t = tplByBizType.get(s.getBizType());
            if (t == null) {
                vo.setEnabled(null);          // 未绑定：既不是启用也不是停用
            } else {
                vo.setTemplateCode(t.getCode());
                vo.setTemplateName(t.getName());
                vo.setTemplateVersion(t.getVersion());
                boolean on = t.getEnabled() != null && t.getEnabled() == 1;
                vo.setTemplateEnabled(on);
                vo.setEnabled(on);
            }
            out.add(vo);
        }
        return R.ok(out);
    }

    @PostMapping("/scenes/register")
    @Permit(PermissionCodes.APPROVAL_MANAGE)
    public R<BizScene> registerScene(@RequestBody BizScene scene) {
        return R.ok(configService.registerScene(scene));
    }

    /**
     * 设置场景的数据表绑定（审批页面「与表绑定 / 真绑定」）。
     * <p>
     * 业务方注册场景时只报字段字典（formSchema/fieldSchema），<b>不</b>会写这一列；
     * 真绑定表由管理员在配置中心单独设置，渲染时按这些表去业务库捞真实行。
     */
    @PutMapping("/scenes/{bizType}/table-bindings")
    @Permit(PermissionCodes.APPROVAL_MANAGE)
    public R<BizScene> saveTableBindings(@PathVariable String bizType,
                                        @Valid @RequestBody SceneTableBindingsDto dto) {
        return R.ok(configService.saveTableBindings(bizType, dto));
    }

    // ---------------------------------------------------------------- 实体元数据代理

    /**
     * 业务库表清单代理：前端「绑定数据表」清单的数据源。
     * <p>
     * 按 appCode 路由到对应业务方（ehr / cms / blog / sso-center / settings 都提供
     * {@code /internal/entity/**}，实现同一份 common 基类）。路由表见
     * {@code approval.entity.base-urls}；没配的应用视为未开通，直接报错让界面说清楚。
     */
    @GetMapping("/entity/tables")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<com.fasterxml.jackson.databind.JsonNode> entityTables(@RequestParam String appCode,
                                                                   jakarta.servlet.http.HttpServletResponse resp) {
        noStore(resp);
        return R.ok(entityDataClient.tables(appCode));
    }

    /** 某业务库表的列元数据代理（字段池「数据表字段」分组的数据源）。 */
    @GetMapping("/entity/tables/{table}/columns")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<com.fasterxml.jackson.databind.JsonNode> entityColumns(@RequestParam String appCode,
                                                                    @PathVariable String table,
                                                                    jakarta.servlet.http.HttpServletResponse resp) {
        noStore(resp);
        return R.ok(entityDataClient.columns(appCode, table));
    }

    /**
     * 元数据接口一律禁缓存。
     * <p>
     * 表清单/列清单是「配置期实时数据」——新建一张表、改一次注释，界面必须立刻看到。
     * 之前前端虽带 {@code _t} 破缓存，但代理响应没给缓存指令，老页面（无破缓存参数）
     * 仍会命中浏览器 HTTP 缓存，出现「表清单少一张/多一行」的假象。
     */
    private void noStore(jakarta.servlet.http.HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        resp.setHeader("Pragma", "no-cache");
        resp.setHeader("Expires", "0");
    }

    /**
     * 已开通数据绑定的应用清单（appCode → 对内基址）。
     * <p>
     * 给配置端做提示用：只有这些应用能选表绑。纯展示型应用（如 portal）自身没有业务库，
     * 出现在这里以外就意味着「绑不了」，不必等到点保存才报错。
     */
    @GetMapping("/entity/apps")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<Map<String, String>> entityApps() {
        return R.ok(entityDataClient.availableApps());
    }

    /**
     * 业务点引用情况：{@code bizType::code -> [引用位置, ...]}。
     * <p>
     * 业务点配置页一次拉全量，用来把「已被引用」的业务点删除按钮置灰并说明原因。
     * 真正的删除拦截在 {@code registerScene} 里，这里只是给界面看的。
     */
    @GetMapping("/biz-points/references")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<Map<String, List<String>>> bizPointReferences() {
        return R.ok(bizPointReferences.all());
    }

    // ---------------------------------------------------------------- 运营

    /**
     * 时效总览：默认按起止日期过滤（不传=全量）。
     * appCode 用于「按应用」看板，orgId 用于「按组织」看板，二者可叠加。
     */
    @GetMapping("/stats")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<ApprovalStatsVO> stats(@RequestParam(required = false) String appCode,
                                    @RequestParam(required = false) String bizType,
                                    @RequestParam(required = false) Long orgId,
                                    @RequestParam(required = false)
                                    @org.springframework.format.annotation.DateTimeFormat(
                                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                    LocalDate from,
                                    @RequestParam(required = false)
                                    @org.springframework.format.annotation.DateTimeFormat(
                                            iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                    LocalDate to) {
        return R.ok(opsService.stats(appCode, bizType, orgId, from, to));
    }

    /** 审批人维度时效：谁手里压单、谁处理得快 */
    @GetMapping("/stats/approver")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<List<ApprovalApproverStatsVO>> approverStats(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate to) {
        return R.ok(opsService.approverStats(appCode, bizType, orgId, from, to));
    }

    /** 积压列表：在途单按当前节点等待时长倒序，超时标红 */
    @GetMapping("/backlog")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<List<ApprovalBacklogVO>> backlog(@RequestParam(required = false) String appCode,
                                              @RequestParam(required = false) String bizType,
                                              @RequestParam(required = false) Long orgId,
                                              @RequestParam(defaultValue = "100") int limit) {
        return R.ok(opsService.backlog(appCode, bizType, orgId, Math.min(Math.max(limit, 1), 200)));
    }

    /** 催办：给当前待办人发站内提醒，instance.urge_count +1 */
    @PostMapping("/instances/{id}/urge")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<ApprInstance> urge(@PathVariable Long id,
                                @RequestBody(required = false) ApprovalActionRequest req) {
        ApprovalActionRequest body = req == null ? new ApprovalActionRequest() : req;
        body.setOperatorId(SecurityUtils.currentUserId());
        body.setOperatorName(SecurityUtils.currentUsername().orElse(""));
        return R.ok(opsService.urge(id, body));
    }

    /** 批量通过/驳回：逐条独立事务，部分失败不回滚 */
    @PostMapping("/tasks/batch")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<BatchApprovalResultVO> batch(@RequestBody BatchApprovalRequest req) {
        // 操作人只能来自当前令牌，避免前端传别人的 operatorId 替人审批
        req.setOperatorId(SecurityUtils.currentUserId());
        req.setOperatorName(SecurityUtils.currentUsername().orElse(""));
        return R.ok(opsService.batch(req));
    }

    /**
     * 导出 CSV：type=pending 我的待办 / instances 审批流水。
     * 待办固定按当前账号过滤，流水按查询条件；导出行为写操作日志。
     */
    @GetMapping("/export")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<String> export(@RequestParam(defaultValue = "pending") String type,
                            @RequestParam(required = false) String appCode,
                            @RequestParam(required = false) String bizType,
                            @RequestParam(required = false) Long orgId,
                            @RequestParam(required = false)
                            @org.springframework.format.annotation.DateTimeFormat(
                                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                            LocalDate from,
                            @RequestParam(required = false)
                            @org.springframework.format.annotation.DateTimeFormat(
                                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                            LocalDate to) {
        Long me = SecurityUtils.currentUserId();
        return R.ok(opsService.export(type, me, appCode, bizType, orgId, from, to, me));
    }

    // ---------------------------------------------------------------- 条件

    /**
     * 条件表达式校验 + 试算。配置器每次编辑条件都会调它，
     * 保存时也会再校验一次——不能指望前端是唯一闸口。
     */
    @PostMapping("/conditions/validate")
    @Permit(PermissionCodes.APPROVAL_VIEW)
    public R<Map<String, Object>> validateCondition(@RequestBody Map<String, Object> body) {
        String expr = body.get("expr") == null ? "" : String.valueOf(body.get("expr"));
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = body.get("vars") instanceof Map
                ? (Map<String, Object>) body.get("vars") : Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        String err = conditionEvaluator.validate(expr, vars);
        result.put("ok", err == null);
        result.put("error", err);
        result.put("result", err == null ? conditionEvaluator.eval(expr, vars) : null);
        return R.ok(result);
    }
}

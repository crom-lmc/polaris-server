package com.laimengchao.sso.account.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.dto.request.ApprovalActionRequest;
import com.laimengchao.sso.account.integration.ApprovalProxyClient;
import com.laimengchao.sso.account.security.Permit;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 审批中心对外接口（统一账号中心代理）。
 * <p>
 * 审批的模板与实例都在 settings，但「谁在审批」由统一账号中心说了算：
 * 本控制器的职责就是<b>补上操作人身份</b>再转给 settings 的内部接口。
 * <p>
 * 这里<b>刻意没有 @Permit</b>：能不能审某张单由「任务分派给谁」决定，不是权限点决定的。
 * 给审批中心加一个 {@code approval:view} 之类的码，只会变成「有码的人能看到别人的单」——
 * 待办列表永远只查 {@code approver_id = 当前登录人}，多一道权限码反而制造越权错觉。
 * 登录态仍然是硬要求（{@link #accountId()} 为空直接 401）。
 * <p>
 * 返回体不做 VO 复制：settings 的 VO 里雪花 ID 全靠注解转字符串，原样透传才不会丢精度。
 */
@RestController
@RequestMapping("/api/accounts/approvals")
@RequiredArgsConstructor
public class ApprovalCenterController {

    private final ApprovalProxyClient approvalClient;

    /** 待办五视图：pending 待我审批 / approved 已审批 / mine 我发起 / cc 抄送我 / delegated 我委托出去的 */
    @GetMapping
    public R<JsonNode> list(@RequestParam(defaultValue = "pending") String scope) {
        return R.ok(approvalClient.list(accountId(), scope));
    }

    /** 单据详情（含步骤条、每个分支的审批人、表单快照、我的待办任务 ID） */
    @GetMapping("/{id}")
    public R<JsonNode> detail(@PathVariable Long id) {
        return R.ok(approvalClient.detail(id, accountId()));
    }

    /**
     * 批量反查某笔业务的最新审批单。
     * <p>
     * 业务回调在终态会清掉业务表上的实例号，业务列表想显示「XX审批被驳回 · 查看审批信息」
     * 只能按 (bizType, bizId) 回到审批中心查历史单；一次请求查完当前页，避免 N 次调用。
     * 同样不设权限码：能否查看单据详情由审批中心既有规则决定（详情接口本身只看登录态）。
     */
    @PostMapping("/latest-batch")
    public R<JsonNode> latestBatch(@RequestBody(required = false) List<Map<String, String>> refs) {
        accountId();
        return R.ok(approvalClient.latestBatch(refs));
    }

    /** 审批弹窗的信息区配置；未配置时 data=null，前端走内置默认版式 */
    @GetMapping("/pages/{bizType}")
    public R<JsonNode> page(@PathVariable String bizType,
                            @RequestParam(defaultValue = "APPROVAL") String view) {
        accountId();
        return R.ok(approvalClient.page(bizType, view));
    }

    /**
     * 审批弹窗渲染上下文：版式 + 已算好的字段值 + 我的待办。
     * 前端只负责画，不参与任何取值与权限判断。
     */
    @GetMapping("/{id}/context")
    public R<JsonNode> context(@PathVariable Long id,
                               @RequestParam(defaultValue = "APPROVAL") String view) {
        return R.ok(approvalClient.context(id, accountId(), view));
    }

    /* ---------------- 动作 ---------------- */

    /** 全量组织树（扁平，前端组树）：转办 / 加签弹窗按组织选人用 */
    @GetMapping("/orgs/tree")
    public R<JsonNode> orgTree() {
        accountId();
        return R.ok(approvalClient.orgTree());
    }

    /** 某组织直属成员（已开户、未离职）：转办 / 加签候选人 */
    @GetMapping("/orgs/{orgId}/employees")
    public R<JsonNode> orgEmployees(@PathVariable Long orgId) {
        accountId();
        return R.ok(approvalClient.orgEmployees(orgId));
    }

    /** 通过 / 驳回 */
    @PostMapping("/tasks/{taskId}/approve")
    public R<JsonNode> approve(@PathVariable Long taskId,
                               @RequestBody(required = false) ApprovalActionRequest req) {
        return R.ok(approvalClient.approve(taskId, accountId(), operatorName(), orEmpty(req)));
    }

    /** 转办：把这条待办转给另一个人（原任务终结，一次性，不能反复转） */
    @PostMapping("/tasks/{taskId}/transfer")
    public R<Void> transfer(@PathVariable Long taskId,
                            @RequestBody ApprovalActionRequest req) {
        approvalClient.transfer(taskId, accountId(), operatorName(), req);
        return R.ok();
    }

    /** 加签：临时把人插进当前节点（会把该节点切成会签，否则或签节点上加签无效） */
    @PostMapping("/tasks/{taskId}/add-sign")
    public R<Void> addSign(@PathVariable Long taskId,
                           @RequestBody ApprovalActionRequest req) {
        approvalClient.addSign(taskId, accountId(), operatorName(), req);
        return R.ok();
    }

    /** 撤回：只有发起人、且流程没结束时可以 */
    @PostMapping("/{id}/withdraw")
    public R<JsonNode> withdraw(@PathVariable Long id,
                                @RequestBody(required = false) ApprovalActionRequest req) {
        return R.ok(approvalClient.withdraw(id, accountId(), operatorName(), orEmpty(req)));
    }

    /** 终止：管理员强制结束在途单 */
    @PostMapping("/{id}/terminate")
    public R<JsonNode> terminate(@PathVariable Long id,
                                 @RequestBody(required = false) ApprovalActionRequest req) {
        return R.ok(approvalClient.terminate(id, accountId(), operatorName(), orEmpty(req)));
    }

    /* ---------------- 审批运营 ---------------- */

    /** 时效总览（HR/超管）：总量、通过/驳回、平均与 P90 时长、积压、驳回率 */
    @GetMapping("/stats")
    @Permit(PermissionConstants.APPROVAL_OPS)
    public R<JsonNode> stats(@RequestParam(required = false) String appCode,
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
        accountId();
        return R.ok(approvalClient.stats(appCode, bizType, orgId, dateStr(from), dateStr(to)));
    }

    /** 审批人维度时效：谁压单、谁处理快 */
    @GetMapping("/stats/approver")
    @Permit(PermissionConstants.APPROVAL_OPS)
    public R<JsonNode> approverStats(
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
        accountId();
        return R.ok(approvalClient.approverStats(appCode, bizType, orgId, dateStr(from), dateStr(to)));
    }

    /** 积压列表：在途单按当前节点等待时长倒序 */
    @GetMapping("/backlog")
    @Permit(PermissionConstants.APPROVAL_OPS)
    public R<JsonNode> backlog(@RequestParam(required = false) String appCode,
                               @RequestParam(required = false) String bizType,
                               @RequestParam(required = false) Long orgId,
                               @RequestParam(required = false) Integer limit) {
        accountId();
        return R.ok(approvalClient.backlog(appCode, bizType, orgId, limit));
    }

    /** 催办：给当前待办人发提醒，次数累加 */
    @PostMapping("/{id}/urge")
    @Permit(PermissionConstants.APPROVAL_OPS)
    public R<JsonNode> urge(@PathVariable Long id,
                            @RequestBody(required = false) ApprovalActionRequest req) {
        return R.ok(approvalClient.urge(id, accountId(), operatorName(), req));
    }

    /**
     * 批量通过/驳回。
     * <p>
     * 不挂 approval:ops——普通审批人也能一次处理多条待办；安全性由 settings 逐条校验
     * 「任务属于当前登录人」保证，operatorId 也只取当前登录身份，不让前端传。
     */
    @PostMapping("/tasks/batch")
    public R<JsonNode> batch(@RequestBody Map<String, Object> body) {
        Long me = accountId();
        Object rawIds = body == null ? null : body.get("taskIds");
        Object passObj = body == null ? null : body.get("pass");
        Object noteObj = body == null ? null : body.get("note");
        Boolean pass = passObj == null ? Boolean.TRUE : Boolean.valueOf(String.valueOf(passObj));
        String note = noteObj == null ? "" : String.valueOf(noteObj);
        return R.ok(approvalClient.batch(rawIds == null ? List.of() : rawIds, pass, note, me, operatorName()));
    }

    /**
     * 导出 CSV：type=pending 我的待办 / instances 审批流水。
     * 返回 R 壳里的字符串，前端自行转 Blob 下载，避免二进制流穿透网关时的编码问题。
     */
    @GetMapping("/export")
    @Permit(PermissionConstants.APPROVAL_OPS)
    public R<JsonNode> export(@RequestParam(defaultValue = "pending") String type,
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
        Long me = accountId();
        return R.ok(approvalClient.export(type, me, appCode, bizType, orgId,
                dateStr(from), dateStr(to), me));
    }

    /* ---------------- 上下文 ---------------- */

    private Long accountId() {
        Long id = AccountContext.getAccountId();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }

    private String operatorName() {
        String name = AccountContext.getUsername();
        return name == null ? "" : name;
    }

    private String dateStr(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private ApprovalActionRequest orEmpty(ApprovalActionRequest req) {
        return req == null ? new ApprovalActionRequest() : req;
    }
}

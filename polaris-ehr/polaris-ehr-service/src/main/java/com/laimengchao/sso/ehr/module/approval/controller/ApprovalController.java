package com.laimengchao.sso.ehr.module.approval.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.ehr.module.approval.dto.HandoverConfirmRequest;
import com.laimengchao.sso.ehr.module.approval.dto.ResignApplyRequest;
import com.laimengchao.sso.ehr.module.approval.service.RegularizeApplyService;
import com.laimengchao.sso.ehr.module.approval.service.RehireApplyService;
import com.laimengchao.sso.ehr.module.approval.service.ResignApplyService;
import com.laimengchao.sso.ehr.module.approval.vo.RegularizeApplyVO;
import com.laimengchao.sso.ehr.module.approval.vo.RehireApplyVO;
import com.laimengchao.sso.ehr.module.approval.vo.ResignApplyVO;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeService;
import com.laimengchao.sso.ehr.module.employee.vo.HandoverCandidateVO;
import com.laimengchao.sso.ehr.security.Permit;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 离职/减员申请与工作交接。
 * <p>
 * <b>审批动作已全部收口到审批中心</b>。EHR 通过 /center 薄代理补上当前账号身份，
 * 在入口校验页签/按钮权限，settings 继续负责流程状态与任务归属校验；
 * 其余接口保留「提交 / 撤销 / 我的单据 / 交接确认」这些业务侧入口。
 */
@Slf4j
@RestController
@RequestMapping("/api/ehr/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ResignApplyService resignApplyService;
    private final RehireApplyService rehireApplyService;
    private final RegularizeApplyService regularizeApplyService;
    private final EmployeeService employeeService;
    private final ApprovalApi approvalApi;

    /* ---------------- EHR 审批中心薄代理 ---------------- */

    /** scope 与页签权限一一对应；列表数据仍由 settings 按当前账号过滤。 */
    @GetMapping("/center")
    @Permit({PermissionCodes.APPROVAL_TODO, PermissionCodes.APPROVAL_APPROVED,
            PermissionCodes.APPROVAL_STARTED, PermissionCodes.APPROVAL_CC, PermissionCodes.APPROVAL_DELEGATED})
    public R<JsonNode> centerList(@RequestParam(defaultValue = "pending") String scope) {
        requirePermission(scopePermission(scope));
        return R.ok(call(() -> approvalApi.pendingRaw(accountId(), scope),
                "/internal/approval/pending"));
    }

    /** 详情沿用账号中心原有登录可见语义，viewerId 只取当前登录账号。 */
    @GetMapping("/center/{id}")
    public R<JsonNode> centerDetail(@PathVariable Long id) {
        return R.ok(call(() -> approvalApi.detailRaw(id, accountId()),
                "/internal/approval/instances/" + id));
    }

    @GetMapping("/center/{id}/context")
    public R<JsonNode> centerContext(@PathVariable Long id,
                                     @RequestParam(defaultValue = "APPROVAL") String view) {
        return R.ok(call(() -> approvalApi.contextRaw(id, view, accountId()),
                "/internal/approval/instances/" + id + "/context"));
    }

    /** 业务列表反查审批结果；保持原共享接口的登录可用行为。 */
    @PostMapping("/center/latest-batch")
    public R<JsonNode> centerLatestBatch(@RequestBody(required = false) List<Map<String, String>> refs) {
        accountId();
        return R.ok(call(() -> approvalApi.latestBatchRaw(refs == null ? List.of() : refs),
                "/internal/approval/instances/latest-batch"));
    }

    /** 通过和驳回共用 settings 接口，但按请求动作分别校验按钮权限。 */
    @PostMapping("/center/tasks/{taskId}/approve")
    @Permit({PermissionCodes.APPROVAL_TODO_APPROVE, PermissionCodes.APPROVAL_TODO_REJECT})
    public R<JsonNode> centerApprove(@PathVariable Long taskId,
                                     @RequestBody(required = false) ApprovalActionDTO req) {
        ApprovalActionDTO action = orEmpty(req);
        boolean pass = action.getPass() == null || action.getPass();
        requirePermission(pass ? PermissionCodes.APPROVAL_TODO_APPROVE : PermissionCodes.APPROVAL_TODO_REJECT);
        Map<String, Object> body = actionBody(action);
        body.put("pass", pass);
        return R.ok(call(() -> approvalApi.approveRaw(taskId, body),
                "/internal/approval/tasks/" + taskId + "/approve"));
    }

    @PostMapping("/center/tasks/{taskId}/transfer")
    @Permit(PermissionCodes.APPROVAL_TODO_TRANSFER)
    public R<JsonNode> centerTransfer(@PathVariable Long taskId,
                                      @RequestBody ApprovalActionDTO req) {
        requireTarget(req, "转办必须指定接手人");
        return R.ok(call(() -> approvalApi.transferRaw(taskId, actionBody(req)),
                "/internal/approval/tasks/" + taskId + "/transfer"));
    }

    @PostMapping("/center/tasks/{taskId}/add-sign")
    @Permit(PermissionCodes.APPROVAL_TODO_ADD_SIGN)
    public R<JsonNode> centerAddSign(@PathVariable Long taskId,
                                     @RequestBody ApprovalActionDTO req) {
        requireTarget(req, "加签必须指定被加签人");
        return R.ok(call(() -> approvalApi.addSignRaw(taskId, actionBody(req)),
                "/internal/approval/tasks/" + taskId + "/add-sign"));
    }

    @PostMapping("/center/{id}/withdraw")
    @Permit(PermissionCodes.APPROVAL_STARTED_WITHDRAW)
    public R<JsonNode> centerWithdraw(@PathVariable Long id,
                                      @RequestBody(required = false) ApprovalActionDTO req) {
        return R.ok(call(() -> approvalApi.withdrawRaw(id, actionBody(orEmpty(req))),
                "/internal/approval/instances/" + id + "/withdraw"));
    }

    @PostMapping("/center/{id}/terminate")
    @Permit(PermissionCodes.APPROVAL_OPS)
    public R<JsonNode> centerTerminate(@PathVariable Long id,
                                       @RequestBody(required = false) ApprovalActionDTO req) {
        return R.ok(call(() -> approvalApi.terminateRaw(id, actionBody(orEmpty(req))),
                "/internal/approval/instances/" + id + "/terminate"));
    }

    @GetMapping("/center/orgs/tree")
    @Permit({PermissionCodes.APPROVAL_TODO_TRANSFER, PermissionCodes.APPROVAL_TODO_ADD_SIGN})
    public R<JsonNode> centerOrgTree() {
        accountId();
        return R.ok(call(approvalApi::orgTreeRaw, "/internal/approval/orgs/tree"));
    }

    @GetMapping("/center/orgs/{orgId}/employees")
    @Permit({PermissionCodes.APPROVAL_TODO_TRANSFER, PermissionCodes.APPROVAL_TODO_ADD_SIGN})
    public R<JsonNode> centerOrgEmployees(@PathVariable Long orgId) {
        accountId();
        return R.ok(call(() -> approvalApi.orgEmployeesRaw(orgId),
                "/internal/approval/orgs/" + orgId + "/employees"));
    }

    private String scopePermission(String scope) {
        String normalized = scope == null ? "pending" : scope;
        return switch (normalized) {
            case "pending" -> PermissionCodes.APPROVAL_TODO;
            case "approved" -> PermissionCodes.APPROVAL_APPROVED;
            case "mine" -> PermissionCodes.APPROVAL_STARTED;
            case "cc" -> PermissionCodes.APPROVAL_CC;
            case "delegated" -> PermissionCodes.APPROVAL_DELEGATED;
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "未知审批视图：" + normalized);
        };
    }

    private void requirePermission(String code) {
        SecurityUser user = currentUser();
        if (!user.isSuperAdmin() && !user.getPermissions().contains(code)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限：" + code);
        }
    }

    private Long accountId() {
        return currentUser().getAccountId();
    }

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    private ApprovalActionDTO orEmpty(ApprovalActionDTO req) {
        return req == null ? new ApprovalActionDTO() : req;
    }

    private Map<String, Object> actionBody(ApprovalActionDTO req) {
        SecurityUser user = currentUser();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operatorId", user.getAccountId());
        body.put("operatorName", user.getUsername() == null ? "" : user.getUsername());
        body.put("pass", req.getPass());
        body.put("note", req.getNote());
        body.put("position", req.getPosition());
        if (req.getTargetId() != null) {
            body.put("targetId", String.valueOf(req.getTargetId()));
        }
        return body;
    }

    private void requireTarget(ApprovalActionDTO req, String message) {
        if (req == null || req.getTargetId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, message);
        }
    }

    private JsonNode call(Supplier<com.laimengchao.sso.settings.client.dto.R<JsonNode>> supplier,
                          String path) {
        try {
            com.laimengchao.sso.settings.client.dto.R<JsonNode> res = supplier.get();
            if (res == null) {
                return null;
            }
            if (!res.ok()) {
                String msg = res.getMessage() == null ? "审批中心返回错误" : res.getMessage();
                log.warn("审批中心返回业务错误：path={} code={} msg={}", path, res.getCode(), msg);
                throw new BizException(res.getCode(), msg);
            }
            return res.getData();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用审批中心失败：path={} err={}", path, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "审批中心暂时不可用，请稍后重试");
        }
    }

    /** scope=mine 我提交的离职/减员申请；scope=all 全部（需查看全部权限） */
    @GetMapping("/resigns")
    @Permit({PermissionCodes.APPROVAL_RESIGN, PermissionCodes.APPROVAL_RESIGN_APPLY, PermissionCodes.RESIGN_APPLY})
    public R<R.Page<ResignApplyVO>> pageResigns(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(resignApplyService.page(scope, status, page, size));
    }

    /** 员工自助提交离职申请（被办理人 = 自己），支持独立自助页和审批中心两个入口。 */
    @PostMapping("/resigns")
    @Permit({PermissionCodes.APPROVAL_RESIGN_APPLY, PermissionCodes.RESIGN_APPLY})
    public R<ResignApplyVO> submitResign(@Valid @RequestBody(required = false) ResignApplyRequest req) {
        return R.ok(resignApplyService.submitSelf(req));
    }

    @PostMapping("/resigns/{id}/cancel")
    @Permit({PermissionCodes.APPROVAL_RESIGN_CANCEL, PermissionCodes.RESIGN_APPLY})
    public R<ResignApplyVO> cancelResign(@PathVariable Long id) {
        return R.ok(resignApplyService.cancel(id));
    }

    /** 单据详情（含交接清单），审批弹窗用 */
    @GetMapping("/resigns/{id}")
    @Permit({PermissionCodes.APPROVAL_RESIGN, PermissionCodes.APPROVAL_RESIGN_APPLY,
            PermissionCodes.APPROVAL_HANDOVER, PermissionCodes.RESIGN_APPLY, PermissionCodes.EMPLOYEE_RESIGN})
    public R<ResignApplyVO> resignDetail(@PathVariable Long id) {
        return R.ok(resignApplyService.detail(id));
    }

    /* ---------------- 工作交接 ---------------- */

    /** 待我确认的交接单；页签鉴权后仍只按 handover_account_id = 当前账号查询。 */
    @GetMapping("/handovers")
    @Permit(PermissionCodes.APPROVAL_HANDOVER)
    public R<List<ResignApplyVO>> myHandovers(
            @RequestParam(defaultValue = "pending") String scope) {
        return R.ok(resignApplyService.myHandovers("all".equalsIgnoreCase(scope)));
    }

    /**
     * 交接人候选（员工自助提交离职用）：同子公司范围内在职且已开户的同事。
     * <p>
     * 同样不挂 {@code @Permit}：普通员工只有 {@code resign:apply}，没有人员列表权限，
     * 但不能因此选不了交接人。数据面只给姓名/工号/部门，不给电话邮箱。
     */
    @GetMapping("/handover-candidates")
    public R<List<HandoverCandidateVO>> handoverCandidates() {
        return R.ok(employeeService.handoverCandidates(SecurityUtils.currentUserId()));
    }

    /**
     * 确认交接项：body.itemIds 为空表示全部确认，传了只确认那几条。
     * 全部确认后申请单才进入「可审批」状态。
     */
    @PostMapping("/resigns/{id}/handover")
    @Permit(PermissionCodes.APPROVAL_HANDOVER_CONFIRM)
    public R<ResignApplyVO> confirmHandover(@PathVariable Long id,
                                            @RequestBody(required = false) HandoverConfirmRequest req) {
        return R.ok(resignApplyService.confirmHandover(id, req));
    }

    /* ---------------- 复职申请（ehr.rehire 审批） ---------------- */

    /** scope=mine 我提交的复职申请；scope=all 全部（需离职/复职管理权限） */
    @GetMapping("/rehires")
    @Permit(PermissionCodes.EMPLOYEE_RESIGN)
    public R<R.Page<RehireApplyVO>> pageRehires(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(rehireApplyService.page(scope, status, page, size));
    }

    @GetMapping("/rehires/{id}")
    @Permit(PermissionCodes.EMPLOYEE_RESIGN)
    public R<RehireApplyVO> rehireDetail(@PathVariable Long id) {
        return R.ok(rehireApplyService.detail(id));
    }

    @PostMapping("/rehires/{id}/cancel")
    @Permit(PermissionCodes.EMPLOYEE_RESIGN)
    public R<RehireApplyVO> cancelRehire(@PathVariable Long id) {
        return R.ok(rehireApplyService.cancel(id));
    }

    /* ---------------- 转正申请（ehr.regularize 审批） ---------------- */

    /**
     * scope=mine 我提交的转正申请；scope=all 全部（需审批权限）。
     * <p>
     * 放开 {@code regularize:apply}：普通员工只有 employee:view，
     * 拿不到 employee:edit，但转正是本人的事，得能在自助页提交与查看自己的单。
     * scope=mine 已在服务端按申请人账号过滤，普通员工只能看到自己的单。
     */
    @GetMapping("/regularizes")
    @Permit({PermissionCodes.EMPLOYEE_EDIT, PermissionCodes.REGULARIZE_APPLY})
    public R<R.Page<RegularizeApplyVO>> pageRegularizes(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(regularizeApplyService.page(scope, status, page, size));
    }

    @GetMapping("/regularizes/{id}")
    @Permit({PermissionCodes.EMPLOYEE_EDIT, PermissionCodes.REGULARIZE_APPLY})
    public R<RegularizeApplyVO> regularizeDetail(@PathVariable Long id) {
        return R.ok(regularizeApplyService.detail(id));
    }

    @PostMapping("/regularizes/{id}/cancel")
    @Permit({PermissionCodes.EMPLOYEE_EDIT, PermissionCodes.REGULARIZE_APPLY})
    public R<RegularizeApplyVO> cancelRegularize(@PathVariable Long id) {
        return R.ok(regularizeApplyService.cancel(id));
    }
}

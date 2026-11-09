package com.laimengchao.sso.ehr.module.employee.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.approval.dto.RehireApplyRequest;
import com.laimengchao.sso.ehr.module.approval.dto.ResignApplyRequest;
import com.laimengchao.sso.ehr.module.approval.service.RehireApplyService;
import com.laimengchao.sso.ehr.module.approval.service.ResignApplyService;
import com.laimengchao.sso.ehr.module.approval.vo.RehireApplyVO;
import com.laimengchao.sso.ehr.module.approval.vo.ResignApplyVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalActionRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeChangeRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeRequest;
import com.laimengchao.sso.ehr.module.employee.dto.ReinstateRequest;
import com.laimengchao.sso.ehr.module.employee.dto.ResignRequest;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeChangeService;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeService;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeChangeResultVO;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeVO;
import com.laimengchao.sso.ehr.security.Permit;
import com.laimengchao.sso.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 人员管理。创建人员时同时跨服务开户（默认开通 sso-center / portal / ehr），
 * 开户得到的登录名与初始密码只在响应里出现一次。
 */
@RestController
@RequestMapping("/api/ehr/employees")
@RequiredArgsConstructor
public class AdminEmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeChangeService employeeChangeService;
    private final ResignApplyService resignApplyService;
    private final RehireApplyService rehireApplyService;

    @GetMapping
    @Permit(PermissionCodes.EMPLOYEE_VIEW)
    public R<R.Page<EmployeeVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer status) {
        return R.ok(employeeService.page(keyword, orgId, status, page, size));
    }

    /** 当前登录人的员工档案（可能为空：账号存在但没有档案） */
    @GetMapping("/me")
    public R<EmployeeVO> me() {
        Long accountId = SecurityUtils.currentUserId();
        return R.ok(employeeService.current(accountId));
    }

    /** 导出人员数据（CSV）：只含当前数据范围内的人，敏感字段按权限掩码，并记 EXPORT 审计 */
    @GetMapping("/export")
    @Permit(PermissionCodes.EMPLOYEE_EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Integer status) {
        String csv = employeeService.exportCsv(keyword, orgId, status);
        // 带 UTF-8 BOM，Excel 直接双击打开不会乱码
        byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"employees-" + LocalDate.now() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.EMPLOYEE_VIEW)
    public R<EmployeeVO> detail(@PathVariable Long id) {
        return R.ok(employeeService.detail(id));
    }

    /** 查看手机号 / 身份证 / 紧急联系电话明文（有 employee:sensitive:view，且留 PLAIN 审计） */
    @GetMapping("/{id}/sensitive")
    @Permit(PermissionCodes.EMPLOYEE_SENSITIVE_VIEW)
    public R<EmployeeVO> sensitive(@PathVariable Long id) {
        return R.ok(employeeService.sensitiveView(id));
    }

    @PostMapping
    @Permit(PermissionCodes.EMPLOYEE_CREATE)
    public R<EmployeeVO> create(@Valid @RequestBody EmployeeRequest req) {
        return R.ok(employeeService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeVO> update(@PathVariable Long id, @RequestBody EmployeeRequest req) {
        return R.ok(employeeService.update(id, req));
    }

    /**
     * 人员信息变更提交（试用期/在职）：基本信息差异 + 四类子表目标态整表提交，
     * 服务端比对出字段级/行级差异落变更单并发起审批，审批通过才正式落库。
     */
    @PostMapping("/{id}/change-apply")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeChangeResultVO> submitChange(@PathVariable Long id,
                                                  @Valid @RequestBody EmployeeChangeRequest req) {
        return R.ok(employeeChangeService.submit(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.EMPLOYEE_DELETE)
    public R<Void> delete(@PathVariable Long id) {
        employeeService.delete(id);
        return R.ok();
    }

    /** 补开户：给已存在但没绑账号的员工开通统一账号 */
    @PostMapping("/{id}/account")
    @Permit(PermissionCodes.EMPLOYEE_ACCOUNT)
    public R<EmployeeVO> openAccount(@PathVariable Long id) {
        return R.ok(employeeService.openAccount(id));
    }

    /** 办理离职：档案置离职 + 记录日期原因，默认同步停用统一账号 */
    @PostMapping("/{id}/resign")
    @Permit(PermissionCodes.EMPLOYEE_RESIGN)
    public R<EmployeeVO> resign(@PathVariable Long id,
                               @RequestBody(required = false) ResignRequest req) {
        return R.ok(employeeService.resign(id, req));
    }

    /** 关闭账号：「待关闭账号」→「已离职」，并停用统一账号（离职/减员审批通过后的最后一步） */
    @PostMapping("/{id}/close-account")
    @Permit(PermissionCodes.EMPLOYEE_RESIGN)
    public R<EmployeeVO> closeAccount(@PathVariable Long id) {
        return R.ok(employeeService.closeAccount(id));
    }

    /**
     * 减员：HR 在人员列表上发起，只落一张申请单，等部门负责人审批通过才真正离职。
     * <p>
     * 与上面的 /resign 的区别：那个是直办（纠错/追溯用），这个是正常流程。
     */
    @PostMapping("/{id}/reduce")
    @Permit(PermissionCodes.EMPLOYEE_RESIGN)
    public R<ResignApplyVO> reduce(@PathVariable Long id,
                                   @Valid @RequestBody(required = false) ResignApplyRequest req) {
        return R.ok(resignApplyService.submitReduce(id, req));
    }

    /**
     * 复职申请：只落一张申请单并发起 {@code ehr.rehire} 审批，
     * 审批通过后才由回调把档案置回在职并启用账号。
     * <p>
     * 与下面的 /reinstate 的区别：那个是直办（历史数据纠错用），这个是正常流程。
     */
    @PostMapping("/{id}/rehire-apply")
    @Permit(PermissionCodes.EMPLOYEE_RESIGN)
    public R<RehireApplyVO> rehireApply(@PathVariable Long id,
                                        @Valid @RequestBody(required = false) RehireApplyRequest req) {
        return R.ok(rehireApplyService.submit(id, req));
    }

    /** 复职：恢复在职并清空离职信息，默认重新启用统一账号（直办，不走审批） */
    @PostMapping("/{id}/reinstate")
    @Permit(PermissionCodes.EMPLOYEE_RESIGN)
    public R<EmployeeVO> reinstate(@PathVariable Long id,
                                   @RequestBody(required = false) ReinstateRequest req) {
        return R.ok(employeeService.reinstate(id, req));
    }

    /** 待入职草稿提交：进入入职审批中，并开通统一账号 */
    @PostMapping("/{id}/submit-draft")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeVO> submitDraft(@PathVariable Long id) {
        return R.ok(employeeService.submitDraft(id));
    }

    /** 入职审批：通过/驳回当前步骤（用人部门 → 人事主管） */
    @PostMapping("/{id}/onboard-approve")
    @Permit(PermissionCodes.APPROVAL_APPROVE)
    public R<EmployeeVO> onboardApprove(@PathVariable Long id,
                                       @RequestBody(required = false) ApprovalActionRequest req) {
        boolean pass = req == null || req.getPass() == null || req.getPass();
        String note = req == null ? null : req.getNote();
        return R.ok(employeeService.approveOnboard(id, pass, note));
    }

    /**
     * 办理入职：仅「入职审批通过」（25）可调，由 HR 在人员列表点「入职」确认到岗。
     * 免试用期 → 直接正式（40）；否则进入试用期（30）。审批阶段已开通的账号沿用。
     */
    @PostMapping("/{id}/onboard")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeVO> onboard(@PathVariable Long id) {
        return R.ok(employeeService.onboard(id));
    }

    /**
     * 试用期员工自助提交转正申请（本人或 HR 均可）。
     * <p>
     * 请求体可选：带了就填申请单（自评 / 业绩 / 理由 / 后续计划），
     * 不带就退化成老行为（只发起审批，不填内容）。
     */
    @PostMapping("/{id}/regularize-apply")
    public R<EmployeeVO> regularizeApply(@PathVariable Long id,
                                         @Valid @RequestBody(required = false)
                                         com.laimengchao.sso.ehr.module.approval.dto.RegularizeApplyRequest req) {
        return R.ok(employeeService.applyRegularize(id, req));
    }

}

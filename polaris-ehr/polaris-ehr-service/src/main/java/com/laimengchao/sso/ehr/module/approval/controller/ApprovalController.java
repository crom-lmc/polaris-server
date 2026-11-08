package com.laimengchao.sso.ehr.module.approval.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
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
import com.laimengchao.sso.ehr.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 离职/减员申请与工作交接。
 * <p>
 * <b>审批动作已全部收口到审批中心</b>（统一账号中心 /api/accounts/approvals/**），
 * 这里只保留「提交 / 撤销 / 我的单据 / 交接确认」这些业务侧入口；
 * 待我审批、通过、驳回、撤回、转办、加签都在审批中心完成。
 */
@RestController
@RequestMapping("/api/ehr/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ResignApplyService resignApplyService;
    private final RehireApplyService rehireApplyService;
    private final RegularizeApplyService regularizeApplyService;
    private final EmployeeService employeeService;

    /** scope=mine 我提交的离职/减员申请；scope=all 全部（需审批权限） */
    @GetMapping("/resigns")
    @Permit({PermissionCodes.APPROVAL_VIEW, PermissionCodes.APPROVAL_APPROVE, PermissionCodes.RESIGN_APPLY})
    public R<R.Page<ResignApplyVO>> pageResigns(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(resignApplyService.page(scope, status, page, size));
    }

    /** 员工自助提交离职申请（被办理人 = 自己） */
    @PostMapping("/resigns")
    @Permit(PermissionCodes.RESIGN_APPLY)
    public R<ResignApplyVO> submitResign(@Valid @RequestBody(required = false) ResignApplyRequest req) {
        return R.ok(resignApplyService.submitSelf(req));
    }

    @PostMapping("/resigns/{id}/cancel")
    @Permit(PermissionCodes.RESIGN_APPLY)
    public R<ResignApplyVO> cancelResign(@PathVariable Long id) {
        return R.ok(resignApplyService.cancel(id));
    }

    /** 单据详情（含交接清单），审批弹窗用 */
    @GetMapping("/resigns/{id}")
    @Permit({PermissionCodes.APPROVAL_VIEW, PermissionCodes.APPROVAL_APPROVE,
            PermissionCodes.RESIGN_APPLY, PermissionCodes.EMPLOYEE_RESIGN})
    public R<ResignApplyVO> resignDetail(@PathVariable Long id) {
        return R.ok(resignApplyService.detail(id));
    }

    /* ---------------- 工作交接 ---------------- */

    /**
     * 待我确认的交接单（交接人视角）。
     * <p>
     * 刻意不挂 {@code @Permit}：任何员工都可能被指定成交接人，
     * 给他单独发一个权限码不如直接按「handover_account_id = 当前账号」鉴权。
     */
    @GetMapping("/handovers")
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
    public R<ResignApplyVO> confirmHandover(@PathVariable Long id,
                                            @RequestBody(required = false) HandoverConfirmRequest req) {
        return R.ok(resignApplyService.confirmHandover(id, req));
    }

    /* ---------------- 复职申请（ehr.rehire 审批） ---------------- */

    /** scope=mine 我提交的复职申请；scope=all 全部（需审批权限） */
    @GetMapping("/rehires")
    @Permit({PermissionCodes.APPROVAL_VIEW, PermissionCodes.APPROVAL_APPROVE, PermissionCodes.EMPLOYEE_RESIGN})
    public R<R.Page<RehireApplyVO>> pageRehires(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(rehireApplyService.page(scope, status, page, size));
    }

    @GetMapping("/rehires/{id}")
    @Permit({PermissionCodes.APPROVAL_VIEW, PermissionCodes.APPROVAL_APPROVE, PermissionCodes.EMPLOYEE_RESIGN})
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
    @Permit({PermissionCodes.APPROVAL_VIEW, PermissionCodes.APPROVAL_APPROVE,
            PermissionCodes.EMPLOYEE_EDIT, PermissionCodes.REGULARIZE_APPLY})
    public R<R.Page<RegularizeApplyVO>> pageRegularizes(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(regularizeApplyService.page(scope, status, page, size));
    }

    @GetMapping("/regularizes/{id}")
    @Permit({PermissionCodes.APPROVAL_VIEW, PermissionCodes.APPROVAL_APPROVE,
            PermissionCodes.EMPLOYEE_EDIT, PermissionCodes.REGULARIZE_APPLY})
    public R<RegularizeApplyVO> regularizeDetail(@PathVariable Long id) {
        return R.ok(regularizeApplyService.detail(id));
    }

    @PostMapping("/regularizes/{id}/cancel")
    @Permit({PermissionCodes.APPROVAL_VIEW, PermissionCodes.APPROVAL_APPROVE,
            PermissionCodes.EMPLOYEE_EDIT, PermissionCodes.REGULARIZE_APPLY})
    public R<RegularizeApplyVO> cancelRegularize(@PathVariable Long id) {
        return R.ok(regularizeApplyService.cancel(id));
    }
}

package com.laimengchao.sso.ehr.module.ledger.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.ledger.service.HrLedgerService;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.AttendanceLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.ContractLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.LeaveLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.MovementLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.OvertimeLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RecruitmentLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.RegularizationLedger;
import com.laimengchao.sso.ehr.module.ledger.vo.HrLedgerVO.WorkforceLedger;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 人力台账只读报表。 */
@RestController
@RequestMapping("/api/ehr/hr-ledgers")
@RequiredArgsConstructor
public class HrLedgerController {

    private final HrLedgerService ledgerService;

    @GetMapping("/contracts")
    @Permit(PermissionCodes.LEDGER_CONTRACT)
    public R<ContractLedger> contracts(@RequestParam(required = false) Long orgId,
                                       @RequestParam(defaultValue = "ALL") String expiry,
                                       @RequestParam(defaultValue = "ALL") String type,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "6") int size) {
        return R.ok(ledgerService.contracts(orgId, expiry, type, keyword, page, size));
    }

    @GetMapping("/movement")
    @Permit(PermissionCodes.LEDGER_MOVEMENT)
    public R<MovementLedger> movement(@RequestParam(required = false) String fromMonth,
                                      @RequestParam(required = false) String toMonth,
                                      @RequestParam(required = false) Long orgId,
                                      @RequestParam(defaultValue = "ALL") String event,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "6") int size) {
        return R.ok(ledgerService.movement(fromMonth, toMonth, orgId, event, page, size));
    }

    @GetMapping("/attendance")
    @Permit(PermissionCodes.LEDGER_ATTENDANCE)
    public R<AttendanceLedger> attendance(@RequestParam(required = false) String month,
                                          @RequestParam(required = false) Long orgId,
                                          @RequestParam(defaultValue = "ALL") String type,
                                          @RequestParam(defaultValue = "ALL") String status,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "6") int size) {
        return R.ok(ledgerService.attendance(month, orgId, type, status, keyword, page, size));
    }

    @GetMapping("/workforce")
    @Permit(PermissionCodes.LEDGER_WORKFORCE)
    public R<WorkforceLedger> workforce(@RequestParam(required = false) Long orgId,
                                        @RequestParam(defaultValue = "ALL") String status,
                                        @RequestParam(required = false) String position,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return R.ok(ledgerService.workforce(orgId, status, position, keyword, page, size));
    }

    @GetMapping("/regularization")
    @Permit(PermissionCodes.LEDGER_REGULARIZATION)
    public R<RegularizationLedger> regularization(@RequestParam(required = false) Long orgId,
                                                  @RequestParam(defaultValue = "ALL") String expiry,
                                                  @RequestParam(defaultValue = "ALL") String approvalStatus,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return R.ok(ledgerService.regularization(orgId, expiry, approvalStatus, keyword, page, size));
    }

    @GetMapping("/leaves")
    @Permit(PermissionCodes.LEDGER_LEAVES)
    public R<LeaveLedger> leaves(@RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to,
                                 @RequestParam(required = false) Long orgId,
                                 @RequestParam(defaultValue = "ALL") String type,
                                 @RequestParam(defaultValue = "ALL") String status,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "10") int size) {
        return R.ok(ledgerService.leaves(from, to, orgId, type, status, keyword, page, size));
    }

    @GetMapping("/overtimes")
    @Permit(PermissionCodes.LEDGER_OVERTIMES)
    public R<OvertimeLedger> overtimes(@RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) Long orgId,
                                       @RequestParam(defaultValue = "ALL") String type,
                                       @RequestParam(defaultValue = "ALL") String status,
                                       @RequestParam(defaultValue = "ALL") String settleStatus,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return R.ok(ledgerService.overtimes(from, to, orgId, type, status, settleStatus, keyword, page, size));
    }

    @GetMapping("/recruitment")
    @Permit(PermissionCodes.LEDGER_RECRUITMENT)
    public R<RecruitmentLedger> recruitment(@RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) Long orgId,
                                            @RequestParam(defaultValue = "ALL") String stage,
                                            @RequestParam(required = false) String source,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return R.ok(ledgerService.recruitment(from, to, orgId, stage, source, keyword, page, size));
    }

}

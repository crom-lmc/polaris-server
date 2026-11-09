package com.laimengchao.sso.ehr.module.contract.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.contract.dto.ContractRenewRequest;
import com.laimengchao.sso.ehr.module.contract.dto.ContractRequest;
import com.laimengchao.sso.ehr.module.contract.dto.ContractTerminateRequest;
import com.laimengchao.sso.ehr.module.contract.service.ContractService;
import com.laimengchao.sso.ehr.module.contract.vo.ContractVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 员工合同：列表 / 新增 / 编辑 / 续签 / 终止。 */
@RestController
@RequestMapping("/api/ehr/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    @Permit(PermissionCodes.CONTRACT_VIEW)
    public R<List<ContractVO>> list(@RequestParam Long employeeId) {
        return R.ok(contractService.listByEmployee(employeeId));
    }

    @PostMapping
    @Permit(PermissionCodes.CONTRACT_MANAGE)
    public R<ContractVO> create(@RequestParam Long employeeId, @RequestBody ContractRequest req) {
        return R.ok(contractService.create(employeeId, req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.CONTRACT_MANAGE)
    public R<ContractVO> update(@PathVariable Long id, @RequestBody ContractRequest req) {
        return R.ok(contractService.update(id, req));
    }

    @PostMapping("/{id}/renew")
    @Permit(PermissionCodes.CONTRACT_MANAGE)
    public R<ContractVO> renew(@PathVariable Long id, @RequestBody ContractRenewRequest req) {
        return R.ok(contractService.renew(id, req));
    }

    @PostMapping("/{id}/terminate")
    @Permit(PermissionCodes.CONTRACT_MANAGE)
    public R<ContractVO> terminate(@PathVariable Long id, @RequestBody ContractTerminateRequest req) {
        return R.ok(contractService.terminate(id, req));
    }
}

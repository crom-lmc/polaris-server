package com.laimengchao.sso.ehr.module.employee.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.BankRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.EducationRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.FamilyRequest;
import com.laimengchao.sso.ehr.module.employee.dto.EmployeeProfileDtos.WorkRequest;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeProfileService;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeProfileVO;
import com.laimengchao.sso.ehr.security.Permit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员子档案：银行卡 / 教育背景 / 工作经历 / 家庭成员。
 * <p>
 * 查看走 {@code employee:view}（EHR_BASIC 就能看），维护走 {@code employee:edit}（HR）——
 * 与人员列表「查看/编辑」按钮的权限口径一致，不另设权限码。
 */
@RestController
@RequestMapping("/api/ehr/employees/{employeeId}")
@RequiredArgsConstructor
public class EmployeeProfileController {

    private final EmployeeProfileService profileService;

    /** 主档案 + 四类子档案聚合，给「查看」抽屉一次拉全 */
    @GetMapping("/profile")
    @Permit(PermissionCodes.EMPLOYEE_VIEW)
    public R<EmployeeProfileVO> profile(@PathVariable Long employeeId) {
        return R.ok(profileService.profile(employeeId));
    }

    /* ---- 银行卡 ---- */

    @PostMapping("/banks")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeProfileVO.BankVO> addBank(@PathVariable Long employeeId,
                                               @Valid @RequestBody BankRequest req) {
        return R.ok(profileService.addBank(employeeId, req));
    }

    @PutMapping("/banks/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeProfileVO.BankVO> updateBank(@PathVariable Long employeeId, @PathVariable Long id,
                                                  @Valid @RequestBody BankRequest req) {
        return R.ok(profileService.updateBank(employeeId, id, req));
    }

    @DeleteMapping("/banks/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<Void> deleteBank(@PathVariable Long employeeId, @PathVariable Long id) {
        profileService.deleteBank(employeeId, id);
        return R.ok();
    }

    /* ---- 教育背景 ---- */

    @PostMapping("/educations")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeProfileVO.EducationVO> addEducation(@PathVariable Long employeeId,
                                                         @Valid @RequestBody EducationRequest req) {
        return R.ok(profileService.addEducation(employeeId, req));
    }

    @PutMapping("/educations/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeProfileVO.EducationVO> updateEducation(@PathVariable Long employeeId, @PathVariable Long id,
                                                            @Valid @RequestBody EducationRequest req) {
        return R.ok(profileService.updateEducation(employeeId, id, req));
    }

    @DeleteMapping("/educations/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<Void> deleteEducation(@PathVariable Long employeeId, @PathVariable Long id) {
        profileService.deleteEducation(employeeId, id);
        return R.ok();
    }

    /* ---- 工作经历 ---- */

    @PostMapping("/works")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeProfileVO.WorkVO> addWork(@PathVariable Long employeeId,
                                               @Valid @RequestBody WorkRequest req) {
        return R.ok(profileService.addWork(employeeId, req));
    }

    @PutMapping("/works/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeProfileVO.WorkVO> updateWork(@PathVariable Long employeeId, @PathVariable Long id,
                                                  @Valid @RequestBody WorkRequest req) {
        return R.ok(profileService.updateWork(employeeId, id, req));
    }

    @DeleteMapping("/works/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<Void> deleteWork(@PathVariable Long employeeId, @PathVariable Long id) {
        profileService.deleteWork(employeeId, id);
        return R.ok();
    }

    /* ---- 家庭成员 ---- */

    @PostMapping("/families")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeProfileVO.FamilyVO> addFamily(@PathVariable Long employeeId,
                                                   @Valid @RequestBody FamilyRequest req) {
        return R.ok(profileService.addFamily(employeeId, req));
    }

    @PutMapping("/families/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<EmployeeProfileVO.FamilyVO> updateFamily(@PathVariable Long employeeId, @PathVariable Long id,
                                                      @Valid @RequestBody FamilyRequest req) {
        return R.ok(profileService.updateFamily(employeeId, id, req));
    }

    @DeleteMapping("/families/{id}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<Void> deleteFamily(@PathVariable Long employeeId, @PathVariable Long id) {
        profileService.deleteFamily(employeeId, id);
        return R.ok();
    }
}

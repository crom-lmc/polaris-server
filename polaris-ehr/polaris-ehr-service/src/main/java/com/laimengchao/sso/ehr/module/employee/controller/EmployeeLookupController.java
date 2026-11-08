package com.laimengchao.sso.ehr.module.employee.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeService;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeLookupDetailVO;
import com.laimengchao.sso.ehr.module.employee.vo.EmployeeLookupVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工快捷查询：统一账号控制台（sso-web）里按姓名/工号找人用。
 * <p>
 * 不加 @Permit：控制台令牌的作用域是 sso-center，PermissionAspect 会把「非 ehr 作用域」直接拒掉；
 * 因此这里改为在 Service 内做登录校验，且只返回非隐私字段（姓名/职位/邮件，详情再加工号/账号/性别/部门）。
 */
@RestController
@RequestMapping("/api/ehr/employees/lookup")
@RequiredArgsConstructor
public class EmployeeLookupController {

    private final EmployeeService employeeService;

    /** 关键字匹配：工号精确优先，其次姓名模糊；最多 10 条 */
    @GetMapping
    public R<List<EmployeeLookupVO>> search(@RequestParam("keyword") String keyword) {
        return R.ok(employeeService.lookupEmployees(keyword));
    }

    /** 详情：姓名 / 工号 / 账号 / 性别 / 职位 / 部门 / 邮件 */
    @GetMapping("/{employeeId}")
    public R<EmployeeLookupDetailVO> detail(@PathVariable Long employeeId) {
        return R.ok(employeeService.lookupEmployeeDetail(employeeId));
    }
}

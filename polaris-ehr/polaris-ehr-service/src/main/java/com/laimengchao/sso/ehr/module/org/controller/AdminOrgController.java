package com.laimengchao.sso.ehr.module.org.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.org.dto.OrgLeaderRequest;
import com.laimengchao.sso.ehr.module.org.dto.OrgNodeRequest;
import com.laimengchao.sso.ehr.module.org.service.OrgService;
import com.laimengchao.sso.ehr.module.org.vo.LeaderChangeVO;
import com.laimengchao.sso.ehr.module.org.vo.OrgNodeVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织架构维护。层级规则由 {@link OrgService} 强校验：
 * 集团→子公司/部门/项目，子公司→部门/项目，部门→项目，项目→子项目，子项目为叶子。
 */
@RestController
@RequestMapping("/api/ehr/orgs")
@RequiredArgsConstructor
public class AdminOrgController {

    private final OrgService orgService;

    @GetMapping("/tree")
    @Permit(PermissionCodes.ORG_VIEW)
    public R<List<OrgNodeVO>> tree(@RequestParam(required = false) String keyword) {
        return R.ok(orgService.tree(keyword));
    }

    @GetMapping("/flatten")
    @Permit(PermissionCodes.ORG_VIEW)
    public R<List<OrgNodeVO>> flatten(@RequestParam(required = false) String keyword) {
        return R.ok(orgService.flatten(keyword));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.ORG_VIEW)
    public R<OrgNodeVO> detail(@PathVariable Long id) {
        return R.ok(orgService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.ORG_MANAGE)
    public R<OrgNodeVO> create(@Valid @RequestBody OrgNodeRequest req) {
        return R.ok(orgService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.ORG_MANAGE)
    public R<OrgNodeVO> update(@PathVariable Long id, @Valid @RequestBody OrgNodeRequest req) {
        return R.ok(orgService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.ORG_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return R.ok();
    }

    /** 设置 / 更换负责人：建部门时不填，建完再指定（传 0 表示清除）；指定人选必须填变更原因 */
    @PutMapping("/{id}/leader")
    @Permit(PermissionCodes.ORG_MANAGE)
    public R<OrgNodeVO> setLeader(@PathVariable Long id,
                                  @RequestBody(required = false) OrgLeaderRequest req) {
        return R.ok(orgService.setLeader(id,
                req == null ? null : req.getLeaderEmployeeId(),
                req == null ? null : req.getReason()));
    }

    /** 负责人变更记录（含首次设置 / 更换 / 清除），组织详情的变更履历用 */
    @GetMapping("/{id}/leader-changes")
    @Permit(PermissionCodes.ORG_VIEW)
    public R<List<LeaderChangeVO>> leaderChanges(@PathVariable Long id) {
        return R.ok(orgService.leaderChanges(id));
    }

    /** 启用 / 禁用：有下级或有人时后端拒绝，前端按钮也会提前置灰 */
    @PutMapping("/{id}/status")
    @Permit(PermissionCodes.ORG_MANAGE)
    public R<OrgNodeVO> setStatus(@PathVariable Long id, @RequestParam Integer status) {
        return R.ok(orgService.setStatus(id, status));
    }
}

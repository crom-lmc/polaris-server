package com.laimengchao.sso.ehr.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「这单该谁审」的唯一实现。
 * <p>
 * 规则：从被办理人所在组织沿 {@code path} 由深到浅找，第一个「配了负责人、负责人有账号、没离职、
 * 且不是被办理人本人 / 不是提交人自己」的节点就是审批人。
 * <p>
 * 因此如果离职的人是<b>项目 / 部门 / 子公司负责人</b>，走到他自己那一层时会被跳过，
 * 自然就落到<b>上一级负责人</b>手里——这正是「自己不能批自己离职」的落地点，
 * 不需要额外配一张汇报线表。{@code escalated} 标记「发生上溯」，前端据此提示为什么是上级在审。
 * <p>
 * 找不到（全链路都没配负责人 / 负责人还没开户）时返回 {@code null}，调用方把记录留成「无指定审批人」，
 * 由持有审批权限的人兜底。这样新环境不会因为没配负责人就把请假、离职全堵死。
 */
@Component
@RequiredArgsConstructor
public class ApproverResolver {

    private final OrgNodeMapper orgMapper;
    private final EmployeeMapper employeeMapper;

    /**
     * 审批人快照。
     *
     * @param accountId 审批人账户ID
     * @param name      审批人姓名（员工档案姓名，不是登录名）
     * @param orgName   审批人所在节点名，例如「西安分公司」
     * @param escalated 是否因为「被办理人本人就是下级负责人」而往上一级走
     */
    public record Approver(Long accountId, String name, String orgName, boolean escalated) {
        public boolean exists() {
            return accountId != null;
        }
    }

    /**
     * @param emp                被审批人（请假人 / 离职人）
     * @param applicantAccountId 提交人账户ID，用于排除「自己审自己」
     */
    public Approver resolve(Employee emp, Long applicantAccountId) {
        if (emp == null || emp.getOrgId() == null) {
            return null;
        }
        Map<Long, OrgNode> orgs = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgs.put(n.getId(), n);
        }
        OrgNode self = orgs.get(emp.getOrgId());
        if (self == null) {
            return null;
        }
        List<Long> chain = parsePath(self.getPath());
        if (chain.isEmpty()) {
            chain.add(self.getId());
        }
        // 由深到浅：本部门 → 子公司 → 集团；被办理人本人当负责人的那层会被跳过，于是自然上溯一级
        boolean escalated = false;
        for (int i = chain.size() - 1; i >= 0; i--) {
            OrgNode node = orgs.get(chain.get(i));
            if (node == null || node.getLeaderEmployeeId() == null) {
                continue;
            }
            Employee leader = employeeMapper.selectById(node.getLeaderEmployeeId());
            if (leader == null || leader.getAccountId() == null) {
                // 负责人还没开户：继续往上找，不要把人卡在这一层
                continue;
            }
            if (leader.getStatus() != null && leader.getStatus() == EmployeeStatus.LEFT.getCode()) {
                continue;
            }
            if (leader.getId() != null && leader.getId().equals(emp.getId())) {
                // 被办理人自己就是这个节点的负责人（部门/子公司/项目负责人离职）→ 往上一级
                escalated = true;
                continue;
            }
            if (leader.getAccountId().equals(applicantAccountId)) {
                // HR 自己就是该层负责人时不能自审自批，同样上溯一级
                escalated = true;
                continue;
            }
            return new Approver(leader.getAccountId(), leader.getName(), node.getName(), escalated);
        }
        return null;
    }

    private List<Long> parsePath(String path) {
        List<Long> ids = new ArrayList<>();
        if (path == null || path.isBlank()) {
            return ids;
        }
        for (String part : path.split("/")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(part));
            } catch (NumberFormatException ignored) {
                // 脏 path 跳过
            }
        }
        return ids;
    }
}

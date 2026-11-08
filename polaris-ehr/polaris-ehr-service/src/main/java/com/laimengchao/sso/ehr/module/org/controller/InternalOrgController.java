package com.laimengchao.sso.ehr.module.org.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批人解析用的组织反查接口。
 * <p>
 * 审批模板里「组织负责人」这类规则最终要落到<b>账号</b>上（审批任务按账号派单），
 * 而 EHR 里存的是「负责人员工ID」。所以在组织侧转一道：员工 → 账号。
 * <p>
 * {@code levelOffset} 支持上溯：0 本级、1 上一级……发起人所在部门没人负责时，
 * 往往要找上级单位的人审，这个参数就是配套的。
 * <p>
 * 负责人没开户（account_id 为空）时返回 null，由 settings 按模板的 onEmpty 决定跳过还是阻塞——
 * 这个决策不该由 EHR 替它做。
 */
@Slf4j
@RestController
@RequestMapping("/internal/orgs")
@RequiredArgsConstructor
public class InternalOrgController {

    private final OrgNodeMapper orgNodeMapper;
    private final EmployeeMapper employeeMapper;

    /**
     * 上溯取负责人：先走到 levelOffset 指定的层级；若该级没有设负责人，
     * 继续向上找第一个有负责人的节点（「审批上溯」惯例：子公司没配负责人时，
     * 审批自动落到集团负责人头上，而不是把单子卡死）。
     * 到根都没有就返回 null，交给 settings 按模板的 onEmpty 决定跳过还是阻塞。
     */
    @GetMapping("/{id}/leader")
    public R<Map<String, Object>> leader(@PathVariable Long id,
                                         @RequestParam(defaultValue = "0") int levelOffset) {
        OrgNode node = orgNodeMapper.selectById(id);
        int steps = Math.max(levelOffset, 0);
        while (node != null && steps-- > 0) {
            Long parentId = node.getParentId();
            node = (parentId == null || parentId == 0L) ? null : orgNodeMapper.selectById(parentId);
        }
        // 从目标层级起向上找第一个设了负责人的节点
        while (node != null && node.getLeaderEmployeeId() == null) {
            Long parentId = node.getParentId();
            node = (parentId == null || parentId == 0L) ? null : orgNodeMapper.selectById(parentId);
        }
        return R.ok(leaderRef(node));
    }

    /**
     * 按组织名称精确匹配取负责人——「集团人力资源中心审批」这类职能线审批用。
     * 名称在同级下本来就唯一（checkNameUnique），全树精确匹配不会产生歧义。
     */
    @GetMapping("/leader-by-name")
    public R<Map<String, Object>> leaderByName(@RequestParam String name) {
        if (name == null || name.isBlank()) {
            return R.ok(null);
        }
        OrgNode node = orgNodeMapper.selectOne(new LambdaQueryWrapper<OrgNode>()
                .eq(OrgNode::getName, name.trim())
                .last("limit 1"));
        return R.ok(leaderRef(node));
    }

    /**
     * 通用「取某人 / 某组织的负责人」——审批模板 ORG_LEADER 规则配了 source=FIELD 时用。
     * <p>
     * subjectType=ORG：subject 是组织 ID（数字）或组织名称（中文，按名称精确匹配）。
     * subjectType=EMPLOYEE：subject 是账号 ID / 员工档案 ID（数字）或工号（如 A0001），先定位员工再取其所属组织。
     * <p>
     * 定位到目标组织后：toRoot=true 直接取根（集团）节点的负责人；否则先上溯 levelOffset 级，
     * 再从该级向上找第一个设了负责人的节点（审批上溯惯例：子公司没配负责人时落到集团负责人）。
     * <p>
     * 返回结构与 /leader 一致（单对象或 null），settings 端统一解析。
     */
    @GetMapping("/org-leader")
    public R<Map<String, Object>> orgLeader(@RequestParam String subject,
                                           @RequestParam String subjectType,
                                           @RequestParam(defaultValue = "0") int levelOffset,
                                           @RequestParam(defaultValue = "false") boolean toRoot) {
        OrgNode target = resolveSubjectOrg(subject, subjectType);
        if (target == null) {
            return R.ok(null);
        }
        if (toRoot) {
            // 一路上溯到根（集团）节点，返回其负责人这一个人
            OrgNode root = target;
            while (root.getParentId() != null && root.getParentId() != 0L) {
                OrgNode p = orgNodeMapper.selectById(root.getParentId());
                if (p == null) {
                    break;
                }
                root = p;
            }
            return R.ok(leaderRef(root));
        }
        // 先上溯 levelOffset 级，再从该级向上找第一个设了负责人的节点
        int steps = Math.max(levelOffset, 0);
        OrgNode node = target;
        while (node != null && steps-- > 0) {
            Long parentId = node.getParentId();
            node = (parentId == null || parentId == 0L) ? null : orgNodeMapper.selectById(parentId);
        }
        while (node != null && node.getLeaderEmployeeId() == null) {
            Long parentId = node.getParentId();
            node = (parentId == null || parentId == 0L) ? null : orgNodeMapper.selectById(parentId);
        }
        return R.ok(leaderRef(node));
    }

    /** subject → 目标组织：ORG 按 ID / 名称；EMPLOYEE 按账号ID / 工号定位员工再取其组织 */
    private OrgNode resolveSubjectOrg(String subject, String subjectType) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        String s = subject.trim();
        if ("EMPLOYEE".equalsIgnoreCase(subjectType)) {
            Employee emp;
            if (s.matches("\\d+")) {
                // USER 类业务点的值可能是账号 ID（存量模板），也可能是员工档案 ID
                // （例如请假表单的 employeeId 存的是 ehr_employee.id）；账号优先，查不到再按主键找。
                Long num = Long.valueOf(s);
                emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getAccountId, num).last("limit 1"));
                if (emp == null) {
                    emp = employeeMapper.selectById(num);
                }
            } else {
                emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                        .eq(Employee::getEmpNo, s).last("limit 1"));
            }
            if (emp == null || emp.getOrgId() == null) {
                return null;
            }
            return orgNodeMapper.selectById(emp.getOrgId());
        }
        // ORG：数字当 ID，其余当名称
        if (s.matches("\\d+")) {
            return orgNodeMapper.selectById(Long.valueOf(s));
        }
        return orgNodeMapper.selectOne(new LambdaQueryWrapper<OrgNode>()
                .eq(OrgNode::getName, s).last("limit 1"));
    }

    /** 节点负责人 → 账号引用；节点空 / 未设负责人 / 未开户都返回 null */
    private Map<String, Object> leaderRef(OrgNode node) {
        if (node == null || node.getLeaderEmployeeId() == null) {
            return null;
        }
        Employee emp = employeeMapper.selectById(node.getLeaderEmployeeId());
        if (emp == null || emp.getAccountId() == null) {
            log.debug("组织 {} 的负责人 {} 尚未开户，审批人解析为空", node.getId(), node.getLeaderEmployeeId());
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(emp.getAccountId()));
        m.put("name", emp.getName());
        m.put("orgId", String.valueOf(node.getId()));
        m.put("orgName", node.getName());
        return m;
    }

    /**
     * 全量组织节点（扁平列表，前端自己组树）。
     * <p>
     * 注意 id/parentId 一律转字符串——雪花 ID 超出 JS Number 安全整数范围，
     * 以数字下发会在前端丢精度（选错组织、组错树）。
     * <p>
     * typeName / employeeCount 是给「展示型组织树」用的（账号管理左侧树、配置中心业务点树），
     * 口径与 EHR 组织架构页一致：typeName 是组织类型中文名，employeeCount 是<b>直属在职</b>人数
     * （试用 30 + 在职 40，不含下级、不含待入职与离职），一次 group by 取全量，不做 N+1。
     */
    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        List<OrgNode> all = orgNodeMapper.selectList(
                new LambdaQueryWrapper<OrgNode>().orderByAsc(OrgNode::getSort).orderByAsc(OrgNode::getId));
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : employeeMapper.countGroupByOrg()) {
            if (row.get("orgId") instanceof Number orgId && row.get("cnt") instanceof Number cnt) {
                counts.put(orgId.longValue(), cnt.longValue());
            }
        }
        List<Map<String, Object>> list = new ArrayList<>(all.size());
        for (OrgNode n : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(n.getId()));
            m.put("parentId", n.getParentId() == null || n.getParentId() == 0L ? null : String.valueOf(n.getParentId()));
            m.put("name", n.getName());
            m.put("type", n.getType());
            m.put("typeName", n.typeEnum() == null ? n.getType() : n.typeEnum().getLabel());
            m.put("status", n.getStatus());
            m.put("employeeCount", counts.getOrDefault(n.getId(), 0L));
            list.add(m);
        }
        return R.ok(list);
    }

    /**
     * 某组织的直属成员（仅已开户、未离职的）——审批人候选。
     * <p>
     * 没开户的员工拿不到账号，派不了审批任务，直接过滤掉；
     * 已离职（60）同理。ID 同样转字符串。
     */
    @GetMapping("/{id}/employees")
    public R<List<Map<String, Object>>> employees(@PathVariable Long id) {
        List<Employee> emps = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getOrgId, id)
                .isNotNull(Employee::getAccountId)
                .ne(Employee::getStatus, 60)
                .orderByAsc(Employee::getEmpNo));
        List<Map<String, Object>> list = new ArrayList<>(emps.size());
        for (Employee e : emps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(e.getAccountId()));
            m.put("name", e.getName());
            m.put("empNo", e.getEmpNo());
            list.add(m);
        }
        return R.ok(list);
    }

    /**
     * 某组织<b>及其所有下级组织</b>下的账号 ID——账号管理左侧组织树过滤用。
     * <p>
     * 账号中心不存组织归属（只有 EHR 员工档案有 org_id），所以按组织筛账号必须由这里
     * 把「组织 → 员工 → 账号」翻译一次。与 {@link #employees(Long)} 的差别：
     * ① 含整棵子树（选集团要能看到子公司的人），靠 {@code path} 祖先链前缀一次取出；
     * ② 不过滤离职/停用账号——账号管理要能查到并处理这些账号，审批人候选才需要排除。
     * <p>
     * 雪花 ID 一律转字符串下发，避免 19 位数字在浏览器里丢精度。
     */
    @GetMapping("/{id}/account-ids")
    public R<List<String>> accountIds(@PathVariable Long id) {
        OrgNode org = orgNodeMapper.selectById(id);
        if (org == null || org.getPath() == null || org.getPath().isBlank()) {
            return R.ok(List.of());
        }
        String path = org.getPath();
        List<OrgNode> nodes = orgNodeMapper.selectList(new LambdaQueryWrapper<OrgNode>()
                .nested(q -> q.eq(OrgNode::getPath, path)
                        .or().likeRight(OrgNode::getPath, path + "/")));
        if (nodes.isEmpty()) {
            return R.ok(List.of());
        }
        List<Long> orgIds = new ArrayList<>(nodes.size());
        for (OrgNode n : nodes) {
            orgIds.add(n.getId());
        }
        List<Employee> emps = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getOrgId, orgIds)
                .isNotNull(Employee::getAccountId));
        Set<String> ids = new LinkedHashSet<>();
        for (Employee e : emps) {
            if (e.getAccountId() != null) {
                ids.add(String.valueOf(e.getAccountId()));
            }
        }
        return R.ok(new ArrayList<>(ids));
    }
}

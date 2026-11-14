package com.laimengchao.sso.ehr.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.entity.HandoverItem;
import com.laimengchao.sso.ehr.module.approval.entity.ResignApply;
import com.laimengchao.sso.ehr.module.approval.mapper.ResignApplyMapper;
import com.laimengchao.sso.ehr.module.approval.vo.ApprovalItemVO;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.leave.service.LeaveService;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审批中心的待办聚合：把请假单和离职/减员单摆到同一张列表里。
 * <p>
 * 两张表结构不同但审批语义一致，这里只做「读」和「能否审」的判断，写动作仍回到各自的服务，
 * 避免一个 Service 里出现两套业务规则。
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    /** 待办一次最多捞这么多，内部系统够用；真到量级再改成分页 */
    private static final int TODO_LIMIT = 200;

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "待审批",
            1, "已通过",
            2, "已驳回",
            3, "已撤销",
            ResignApplyService.STATUS_PENDING_HR, "待人事中心审批");

    private final LeaveMapper leaveMapper;
    private final ResignApplyMapper resignMapper;
    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final HandoverService handoverService;
    private final LeaveService leaveService;

    /** type 为空返回全部；LEAVE 只看请假；RESIGN 只看离职/减员 */
    public List<ApprovalItemVO> todo(String type) {
        SecurityUser user = currentUser();
        List<ApprovalItemVO> out = new ArrayList<>();
        if (type == null || type.isBlank() || "LEAVE".equalsIgnoreCase(type)) {
            out.addAll(leaveItems(user));
        }
        if (type == null || type.isBlank() || "RESIGN".equalsIgnoreCase(type)) {
            out.addAll(resignItems(user));
        }
        // 新的排前面
        out.sort(Comparator.comparing(ApprovalItemVO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return out;
    }

    private List<ApprovalItemVO> leaveItems(SecurityUser user) {
        List<LeaveRequest> rows = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getStatus, 0)
                .orderByDesc(LeaveRequest::getCreatedAt)
                .last("limit " + TODO_LIMIT));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Employee> emps = employeeMap();
        Map<Long, OrgNode> orgs = orgMap();
        List<ApprovalItemVO> out = new ArrayList<>();
        for (LeaveRequest r : rows) {
            if (!canApprove(user, r.getAccountId(), r.getApproverAccountId())) {
                continue;
            }
            ApprovalItemVO vo = new ApprovalItemVO();
            vo.setId(r.getId());
            vo.setType("LEAVE");
            vo.setTypeName("请假");
            vo.setApplicantName(r.getApplicantName());
            Employee emp = emps.get(r.getEmployeeId());
            vo.setOrgName(emp == null ? "" : orgName(emp.getOrgId(), orgs));
            String typeName = leaveService.typeName(r.getLeaveType());
            vo.setTitle(typeName + " " + (r.getDays() == null ? "" : r.getDays().stripTrailingZeros().toPlainString()) + " 天");
            vo.setPeriod(r.getStartDate() + " ~ " + r.getEndDate());
            vo.setReason(r.getReason());
            vo.setStatus(r.getStatus());
            vo.setStatusName(STATUS_NAMES.getOrDefault(r.getStatus(), "未知"));
            vo.setCreatedAt(fmt(r.getCreatedAt()));
            vo.setApproverName(r.getApproverName());
            vo.setApproveNote(r.getApproveNote());
            vo.setApprovedAt(fmt(r.getApprovedAt()));
            out.add(vo);
        }
        return out;
    }

    /**
     * 离职 / 减员待办。
     * <p>
     * 两级审批都算「待办」：0 级挂在部门负责人名下，{@code STATUS_PENDING_HR}(4) 挂在
     * 集团人力资源中心负责人名下。只捞 0 会让二级单子从审批中心消失——人事中心的人
     * 在待办里根本看不到，只能去「离职·减员申请」里翻，等于二级审批形同虚设。
     */
    private List<ApprovalItemVO> resignItems(SecurityUser user) {
        List<ResignApply> rows = resignMapper.selectList(new LambdaQueryWrapper<ResignApply>()
                .in(ResignApply::getStatus, 0, ResignApplyService.STATUS_PENDING_HR)
                .orderByDesc(ResignApply::getCreatedAt)
                .last("limit " + TODO_LIMIT));
        if (rows.isEmpty()) {
            return List.of();
        }
        // 交接进度一次性取回来，避免每行查一次
        Map<Long, List<HandoverItem>> handovers = handoverService.mapByApplies(
                rows.stream().map(ResignApply::getId).collect(Collectors.toList()));
        List<ApprovalItemVO> out = new ArrayList<>();
        for (ResignApply r : rows) {
            boolean hrStage = r.getStatus() != null
                    && r.getStatus() == ResignApplyService.STATUS_PENDING_HR;
            // 两个环节的审批人快照存在不同字段上，取错就会出现「人事中心的人看不到自己的待办」
            Long boundApprover = hrStage ? r.getHrApproverAccountId() : r.getApproverAccountId();
            if (!canApprove(user, r.getApplicantAccountId(), boundApprover)) {
                continue;
            }
            ApprovalItemVO vo = new ApprovalItemVO();
            vo.setId(r.getId());
            boolean self = ResignApplyService.SOURCE_SELF.equals(r.getSource());
            vo.setType(self ? "RESIGN_SELF" : "RESIGN_HR");
            vo.setTypeName(self ? "离职申请" : "减员申请");
            vo.setApplicantName(self ? r.getEmployeeName() : r.getApplicantName());
            vo.setOrgName(r.getOrgName());
            vo.setTitle(self ? "员工本人申请离职" : "HR 提交减员");
            vo.setPeriod(r.getResignDate() == null ? "——" : r.getResignDate().toString());
            vo.setReason(r.getReason());
            vo.setStatus(r.getStatus());
            vo.setStatusName(STATUS_NAMES.getOrDefault(r.getStatus(), "未知"));
            vo.setStageName(hrStage ? "集团人力资源中心" : "部门负责人");
            vo.setCreatedAt(fmt(r.getCreatedAt()));
            // 「待 X 审批」要写这一环真正在等的人，不能一直写部门那一位
            vo.setApproverName(hrStage ? r.getHrApproverName() : r.getApproverName());
            vo.setApproveNote(r.getApproveNote());
            vo.setApprovedAt(fmt(r.getApprovedAt()));
            // 交接进度：没做完时审批按钮要置灰，前端不用自己算
            HandoverService.Progress p = HandoverService.progressOf(handovers.get(r.getId()));
            vo.setHandoverName(r.getHandoverName());
            vo.setHandoverDone(p.done());
            vo.setHandoverTotal(p.total());
            vo.setHandoverProgress(p.text());
            vo.setHandoverCompleted(r.getHandoverStatus() != null && r.getHandoverStatus() != 0);
            vo.setHandoverMine(user.getAccountId().equals(r.getHandoverAccountId()));
            out.add(vo);
        }
        return out;
    }

    /**
     * 能否审这一条：不能审自己提交的；绑定了审批人时只有本人（或超管）能审；
     * 没绑定审批人（组织没配负责人）时有审批权限的人都能审。
     */
    private boolean canApprove(SecurityUser user, Long applicantAccountId, Long boundApprover) {
        Long me = user.getAccountId();
        if (me.equals(applicantAccountId)) {
            return false;
        }
        return user.isSuperAdmin() || boundApprover == null || boundApprover.equals(me);
    }

    private Map<Long, Employee> employeeMap() {
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>()).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));
    }

    private Map<Long, OrgNode> orgMap() {
        Map<Long, OrgNode> map = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            map.put(n.getId(), n);
        }
        return map;
    }

    private String orgName(Long orgId, Map<Long, OrgNode> orgs) {
        OrgNode node = orgId == null ? null : orgs.get(orgId);
        if (node == null) {
            return "";
        }
        // 只取末级名字，表格里不需要整条路径
        String path = node.getPath();
        if (path == null || path.isBlank()) {
            return node.getName();
        }
        String[] parts = path.split("/");
        OrgNode last = orgs.get(parseLong(parts[parts.length - 1]));
        return last == null ? node.getName() : last.getName();
    }

    private Long parseLong(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String fmt(java.time.LocalDateTime t) {
        return t == null ? null : t.toString().replace('T', ' ');
    }

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow(() ->
                new BizException(ErrorCode.UNAUTHORIZED));
    }
}

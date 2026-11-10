package com.laimengchao.sso.ehr.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceCorrectionApproveRequest;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceCorrectionRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceCorrection;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceDaily;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendancePunch;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceCorrectionMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceDailyMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendancePunchMapper;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceCorrectionVO;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 补卡：员工申请 → HR 直审 → 通过后插入 CORRECTION 打卡并重算当日。
 * 审批通过前日结果始终是 MISSING_PUNCH，不记旷工。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCorrectionService {

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "待审批",
            1, "已通过",
            2, "已驳回",
            3, "已撤销"
    );

    private final AttendanceCorrectionMapper correctionMapper;
    private final AttendancePunchMapper punchMapper;
    private final AttendanceDailyMapper dailyMapper;
    private final AttendanceDailyService dailyService;
    private final EmployeeMapper employeeMapper;
    private final BizNoService bizNoService;

    public AttendanceCorrectionVO apply(AttendanceCorrectionRequest req) {
        if (req == null || req.getWorkDate() == null || req.getPunchTime() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "补卡日期与补卡时间不能为空");
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写补卡原因");
        }
        Employee emp = currentEmployee();
        String type = req.getType() == null || req.getType().isBlank()
                ? "MISSING_PUNCH" : req.getType().trim().toUpperCase();
        if (!"MISSING_PUNCH".equals(type) && !"OVERTIME_PUNCH".equals(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "补卡类型不合法");
        }
        AttendanceCorrection row = new AttendanceCorrection();
        row.setId(IdWorker.getId());
        row.setCorrectionNo(bizNoService.next("ehr_attendance_correction"));
        row.setEmployeeId(emp.getId());
        row.setWorkDate(req.getWorkDate());
        row.setType(type);
        row.setPunchType(normalizePunchType(req.getPunchType()));
        row.setPunchTime(req.getPunchTime());
        row.setReason(req.getReason().trim());
        row.setStatus(0);
        row.setApplicantAccountId(currentUser().getAccountId());
        row.setApplicantName(nvl(currentUser().getUsername()));
        row.setApproveNote("");
        correctionMapper.insert(row);
        // 有日结果时立即标记「补卡审批中」，列表可提示员工
        dailyMapper.update(null, new LambdaUpdateWrapper<AttendanceDaily>()
                .eq(AttendanceDaily::getEmployeeId, emp.getId())
                .eq(AttendanceDaily::getWorkDate, req.getWorkDate())
                .set(AttendanceDaily::getCorrectionStatus, 1));
        return toVO(row, nvl(emp.getName()));
    }

    public R.Page<AttendanceCorrectionVO> page(Integer status, String month, int page, int size) {
        SecurityUser user = currentUser();
        LambdaQueryWrapper<AttendanceCorrection> w = new LambdaQueryWrapper<>();
        if (!canManage(user)) {
            Employee emp = findEmployee(user.getAccountId());
            if (emp == null) {
                return R.Page.of(List.of(), 0);
            }
            w.eq(AttendanceCorrection::getEmployeeId, emp.getId());
        }
        if (status != null) {
            w.eq(AttendanceCorrection::getStatus, status);
        }
        if (month != null && !month.isBlank()) {
            YearMonth ym = parseMonth(month);
            w.ge(AttendanceCorrection::getWorkDate, ym.atDay(1))
                    .le(AttendanceCorrection::getWorkDate, ym.atEndOfMonth());
        }
        w.orderByDesc(AttendanceCorrection::getCreatedAt).orderByDesc(AttendanceCorrection::getId);
        IPage<AttendanceCorrection> ip = correctionMapper.selectPage(new Page<>(page, size), w);
        Map<Long, String> names = employeeNames(ip.getRecords().stream()
                .map(AttendanceCorrection::getEmployeeId).collect(Collectors.toSet()));
        List<AttendanceCorrectionVO> rows = ip.getRecords().stream()
                .map(c -> toVO(c, names.getOrDefault(c.getEmployeeId(), "")))
                .toList();
        return R.Page.of(rows, ip.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public AttendanceCorrectionVO approve(Long id, AttendanceCorrectionApproveRequest req) {
        AttendanceCorrection row = require(id);
        if (!Integer.valueOf(0).equals(row.getStatus())) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "只有待审批的补卡可以审批");
        }
        LocalDateTime now = LocalDateTime.now();
        String note = req == null || req.getNote() == null ? "" : req.getNote();
        int updated = correctionMapper.update(null, new LambdaUpdateWrapper<AttendanceCorrection>()
                .eq(AttendanceCorrection::getId, id)
                .eq(AttendanceCorrection::getStatus, 0)
                .set(AttendanceCorrection::getStatus, 1)
                .set(AttendanceCorrection::getApproveNote, note)
                .set(AttendanceCorrection::getApprovedAt, now));
        if (updated == 0) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "补卡单已被处理");
        }

        AttendancePunch punch = new AttendancePunch();
        punch.setId(IdWorker.getId());
        punch.setEmployeeId(row.getEmployeeId());
        punch.setPunchTime(row.getPunchTime());
        punch.setPunchType(row.getPunchType());
        punch.setSource("CORRECTION");
        punch.setDeviceNo("");
        punch.setLocation("");
        punch.setRemark("补卡通过 #" + id);
        punchMapper.insert(punch);

        dailyService.generate(row.getWorkDate());
        return toVO(correctionMapper.selectById(id), employeeName(row.getEmployeeId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public AttendanceCorrectionVO reject(Long id, AttendanceCorrectionApproveRequest req) {
        AttendanceCorrection row = require(id);
        if (!Integer.valueOf(0).equals(row.getStatus())) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "只有待审批的补卡可以驳回");
        }
        String note = req == null || req.getNote() == null ? "" : req.getNote();
        int updated = correctionMapper.update(null, new LambdaUpdateWrapper<AttendanceCorrection>()
                .eq(AttendanceCorrection::getId, id)
                .eq(AttendanceCorrection::getStatus, 0)
                .set(AttendanceCorrection::getStatus, 2)
                .set(AttendanceCorrection::getApproveNote, note)
                .set(AttendanceCorrection::getApprovedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "补卡单已被处理");
        }
        dailyService.generate(row.getWorkDate());
        return toVO(correctionMapper.selectById(id), employeeName(row.getEmployeeId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        AttendanceCorrection row = require(id);
        SecurityUser user = currentUser();
        if (!Integer.valueOf(0).equals(row.getStatus())) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "只有待审批的补卡可以撤销");
        }
        if (row.getApplicantAccountId() != null
                && !row.getApplicantAccountId().equals(user.getAccountId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能撤销自己的补卡申请");
        }
        correctionMapper.update(null, new LambdaUpdateWrapper<AttendanceCorrection>()
                .eq(AttendanceCorrection::getId, id)
                .eq(AttendanceCorrection::getStatus, 0)
                .set(AttendanceCorrection::getStatus, 3));
        dailyService.generate(row.getWorkDate());
    }

    /* ---------- 工具 ---------- */

    private AttendanceCorrection require(Long id) {
        AttendanceCorrection row = id == null ? null : correctionMapper.selectById(id);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "补卡申请不存在");
        }
        return row;
    }

    private boolean canManage(SecurityUser user) {
        return user.isSuperAdmin()
                || (user.getPermissions() != null
                && user.getPermissions().contains(PermissionCodes.ATTENDANCE_MANAGE));
    }

    private Employee currentEmployee() {
        Employee emp = findEmployee(currentUser().getAccountId());
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "当前账号未绑定员工档案");
        }
        return emp;
    }

    private Employee findEmployee(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, accountId).last("limit 1"));
    }

    private String employeeName(Long employeeId) {
        Employee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        return emp == null ? "" : nvl(emp.getName());
    }

    private Map<Long, String> employeeNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Employee e : employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getId, ids))) {
            map.put(e.getId(), nvl(e.getName()));
        }
        return map;
    }

    private String normalizePunchType(String type) {
        if (type == null || type.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择补卡类型（上班卡/下班卡）");
        }
        String t = type.trim().toUpperCase();
        if (!"IN".equals(t) && !"OUT".equals(t)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "补卡类型只能是 IN 或 OUT");
        }
        return t;
    }

    private AttendanceCorrectionVO toVO(AttendanceCorrection c, String employeeName) {
        AttendanceCorrectionVO vo = new AttendanceCorrectionVO();
        vo.setId(c.getId());
        vo.setEmployeeId(c.getEmployeeId());
        vo.setEmployeeName(employeeName);
        vo.setWorkDate(c.getWorkDate());
        vo.setType(c.getType());
        vo.setPunchType(c.getPunchType());
        vo.setPunchTime(c.getPunchTime());
        vo.setReason(c.getReason());
        vo.setStatus(c.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(c.getStatus(), String.valueOf(c.getStatus())));
        vo.setApproveNote(nvl(c.getApproveNote()));
        vo.setApprovedAt(c.getApprovedAt());
        vo.setApplicantName(nvl(c.getApplicantName()));
        vo.setCreatedAt(c.getCreatedAt());
        return vo;
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}

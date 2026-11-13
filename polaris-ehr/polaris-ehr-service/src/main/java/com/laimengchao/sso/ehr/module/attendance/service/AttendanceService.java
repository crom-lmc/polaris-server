package com.laimengchao.sso.ehr.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.attendance.dto.OvertimeRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceMonth;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendancePunch;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRule;
import com.laimengchao.sso.ehr.module.attendance.entity.Overtime;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceMonthMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendancePunchMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.OvertimeMapper;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceMonthVO;
import com.laimengchao.sso.ehr.module.attendance.vo.OvertimePreviewVO;
import com.laimengchao.sso.ehr.module.attendance.vo.OvertimeVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveQuotaMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.WorkCalendarMapper;
import com.laimengchao.sso.ehr.module.leave.service.LeaveDaysCalculator;
import com.laimengchao.sso.ehr.module.leave.service.LeaveQuotaService;
import com.laimengchao.sso.ehr.security.DataScopeService;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 轻量考勤（P1-2）：加班申请走审批中心，通过后按小时折算调休额度；
 * 月度汇总以工作日历 + 已通过请假/加班实时计算，HR 确认后锁定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final String BIZ_OVERTIME = "ehr.overtime";
    /** 月累计加班超 8 小时或节假日加班：主管通过后再加一级 HR 终审 */
    public static final String BIZ_OVERTIME_HR = "ehr.overtime.hr";
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";

    private static final Map<String, String> TYPE_NAMES = Map.of(
            "NORMAL", "工作日加班",
            "HOLIDAY", "节假日加班",
            "RESTDAY", "休息日加班"
    );
    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "待审批",
            1, "已通过",
            2, "已驳回",
            3, "已撤销"
    );

    private final OvertimeMapper overtimeMapper;
    private final AttendanceMonthMapper monthMapper;
    private final AttendancePunchMapper punchMapper;
    private final AttendanceRuleService ruleService;
    private final EmployeeMapper employeeMapper;
    private final LeaveMapper leaveMapper;
    private final LeaveQuotaMapper quotaMapper;
    private final WorkCalendarMapper calendarMapper;
    private final LeaveDaysCalculator daysCalculator;
    private final LeaveQuotaService quotaService;
    private final DataScopeService dataScopeService;
    private final ApprovalLauncher approvalLauncher;
    private final ApprovalApi approvalApi;
    private final AttendanceCalendarService calendarService;
    private final AttendanceDailyService dailyService;
    private final BizNoService bizNoService;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /** 1 小时加班折 1 小时调休；额度按天记，折算分母可配 */
    @Value("${ehr.attendance.overtime-hours-per-day:8}")
    private BigDecimal hoursPerDay;

    /* ---------- 加班申请 ---------- */

    public OvertimeVO submit(OvertimeRequest req) {
        Employee emp = currentEmployee();
        if (req.getWorkDate() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择加班日期");
        }
        if (req.getWorkDate().isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能为未来日期提交加班");
        }
        if (req.getStartTime() == null || req.getEndTime() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择加班开始与结束时间");
        }
        // 不允许跨天：start/end 必须在同一天；同一天也不允许 start >= end
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "加班结束时间必须晚于开始时间");
        }
        // 加班时长必须 ≥ 0.5 小时，否则扣除工作时段后没有有效加班
        long totalMinutes = Duration.between(req.getStartTime(), req.getEndTime()).toMinutes();
        if (totalMinutes < 30) {
            throw new BizException(ErrorCode.PARAM_ERROR, "加班时长不得少于 30 分钟");
        }

        // 同一员工同一日期时段不可重叠（status ∈ 0/1 待审批或已通过都要拦）
        checkOvertimeOverlap(emp.getId(), req.getWorkDate(), req.getStartTime(), req.getEndTime());

        // 工作日历自动判定类型（要在 punch 校验之前，因为工作日与休息日 punch 规则不同）
        String dayType = calendarService.resolveDayType(req.getWorkDate());

        // punch 校验：工作日 vs 休息日/节假日口径不同
        validateOvertimePunch(emp.getId(), req.getWorkDate(), req.getStartTime(), req.getEndTime(), dayType);

        String type = "LEGAL_HOLIDAY".equals(dayType) ? "HOLIDAY"
                : ("REST".equals(dayType) ? "RESTDAY" : "NORMAL");

        // 工作日：扣除当天正常工作时段 + 午休重叠分钟；休息日/节假日：全部算加班
        int overtimeMinutes = (overtimeMinutes(req.getStartTime(), req.getEndTime(), dayType, emp, req.getWorkDate()));
        if (overtimeMinutes < 30) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    overtimeMinutes <= 0
                            ? "所选时段扣除工作时间后无有效加班分钟，请调整时段"
                            : "所选时段扣除工作时间后有效加班不足 30 分钟，无法计加班");
        }
        // 0.5 小时粒度向下取整：3.1h → 3.0h，3.9h → 3.5h，0.4h → 0（已被上面 <30 拦截）
        BigDecimal hours = BigDecimal.valueOf((int) Math.floor(overtimeMinutes / 30.0) * 30)
                .divide(new BigDecimal("60"), 1, RoundingMode.HALF_UP);

        SecurityUser user = currentUser();
        Overtime o = new Overtime();
        o.setId(IdWorker.getId());
        o.setOvertimeNo(bizNoService.next("ehr_overtime"));
        o.setEmployeeId(emp.getId());
        o.setAccountId(user.getAccountId());
        o.setWorkDate(req.getWorkDate());
        o.setStartTime(req.getStartTime());
        o.setEndTime(req.getEndTime());
        o.setHours(hours);
        o.setType(type);
        o.setReason(req.getReason() == null ? "" : req.getReason());
        o.setStatus(0);
        o.setCreatedAt(LocalDateTime.now());
        overtimeMapper.insert(o);

        BigDecimal monthTotal = approvedMonthHours(emp, o.getWorkDate()).add(o.getHours());
        String bizType = monthTotal.compareTo(new BigDecimal("8")) > 0 || "HOLIDAY".equals(type)
                ? BIZ_OVERTIME_HR : BIZ_OVERTIME;
        approvalLauncher.launch(
                () -> buildStart(o, emp, user, bizType, monthTotal),
                instanceId -> { /* 实例号已退役：在途统一查审批中心 */ },
                () -> overtimeMapper.update(null, new LambdaUpdateWrapper<Overtime>()
                        .eq(Overtime::getId, o.getId())
                        .eq(Overtime::getStatus, 0)
                        .set(Overtime::getStatus, 3)
                        .set(Overtime::getApproveNote, "发起审批失败，系统自动撤销")));
        Overtime fresh = overtimeMapper.selectById(o.getId());
        return toVO(fresh == null ? o : fresh, user, true);
    }

    /* ---------- 加班申请：试算（前端实时预览） ---------- */

    public OvertimePreviewVO preview(OvertimeRequest req) {
        OvertimePreviewVO vo = new OvertimePreviewVO();
        vo.setTip("");
        if (req.getWorkDate() == null || req.getStartTime() == null || req.getEndTime() == null) {
            vo.setTip("请先完整选择日期与时段");
            return vo;
        }
        if (req.getWorkDate().isAfter(LocalDate.now())) {
            vo.setTip("不能为未来日期提交加班");
            return vo;
        }
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            vo.setTip("结束时间必须晚于开始时间");
            return vo;
        }

        Employee emp = currentEmployee();

        // 重叠检查：同 submit，给前端提前提示
        try {
            checkOvertimeOverlap(emp.getId(), req.getWorkDate(), req.getStartTime(), req.getEndTime());
        } catch (BizException ex) {
            vo.setTip("⚠️ " + ex.getMessage());
            return vo;
        }

        // 自动判定类型（要在 punch 校验之前，因为工作日与休息日 punch 规则不同）
        String dayType = calendarService.resolveDayType(req.getWorkDate());

        // punch 校验：同 validateOvertimePunch，只给 preview 一个 boolean
        LocalDateTime dayStart = req.getWorkDate().atStartOfDay();
        LocalDateTime dayEnd = req.getWorkDate().plusDays(1).atStartOfDay();
        List<AttendancePunch> punches = punchMapper.selectList(new LambdaQueryWrapper<AttendancePunch>()
                .eq(AttendancePunch::getEmployeeId, emp.getId())
                .ge(AttendancePunch::getPunchTime, dayStart)
                .lt(AttendancePunch::getPunchTime, dayEnd)
                .orderByAsc(AttendancePunch::getPunchTime));
        boolean punchOk;
        if (punches == null || punches.isEmpty()) {
            punchOk = false;
        } else if ("REST".equals(dayType) || "LEGAL_HOLIDAY".equals(dayType)) {
            LocalDateTime punchFrom = req.getWorkDate().atTime(req.getStartTime());
            LocalDateTime punchTo = req.getWorkDate().atTime(req.getEndTime());
            punchOk = punches.stream().anyMatch(p ->
                    !p.getPunchTime().isBefore(punchFrom) && !p.getPunchTime().isAfter(punchTo));
        } else {
            LocalDateTime lastPunch = punches.get(punches.size() - 1).getPunchTime();
            punchOk = !lastPunch.isBefore(req.getWorkDate().atTime(req.getEndTime()));
        }
        vo.setPunchExists(punchOk);

        String type = "LEGAL_HOLIDAY".equals(dayType) ? "HOLIDAY"
                : ("REST".equals(dayType) ? "RESTDAY" : "NORMAL");
        vo.setType(type);
        vo.setTypeName(TYPE_NAMES.getOrDefault(type, type));

        // 计算加班分钟
        int totalMinutes = (int) Duration.between(req.getStartTime(), req.getEndTime()).toMinutes();
        int otMinutes = overtimeMinutes(req.getStartTime(), req.getEndTime(), dayType, emp, req.getWorkDate());
        vo.setTotalMinutes(totalMinutes);
        vo.setOvertimeMinutes(otMinutes);

        // 0.5 小时粒度向下取整，不足 30 分钟记 0
        BigDecimal hours = otMinutes >= 30
                ? BigDecimal.valueOf((int) Math.floor(otMinutes / 30.0) * 30)
                        .divide(new BigDecimal("60"), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        vo.setHours(hours);

        if (otMinutes <= 0) {
            vo.setTip("所选时段扣除工作时间后无有效加班分钟，请调整时段");
        } else if (otMinutes < 30) {
            vo.setTip("所选时段扣除工作时间后有效加班不足 30 分钟，无法计加班");
        } else if (!vo.getPunchExists()) {
            vo.setTip("⚠️ 加班日期没有打卡记录，无法提交");
        } else {
            int deducted = totalMinutes - otMinutes;
            String detail = deducted > 0
                    ? "共 " + totalMinutes + " 分钟，扣除工作重叠 " + deducted + " 分钟"
                    : "共 " + totalMinutes + " 分钟（" + (type.equals("NORMAL") ? "工作日全额计加班" : "非工作日全额计加班") + "）";
            vo.setTip(detail + "，有效加班 " + hours + " 小时");
        }
        return vo;
    }

    /**
     * 计算加班有效分钟数。
     * <p>
     * 工作日（NORMAL）：扣除员工当天工作时段 + 午休重叠分钟；
     * 休息日（REST）/ 节假日（LEGAL_HOLIDAY）：整个区间都算加班分钟。
     * 不允许跨夜班（crossDay），若规则存在跨夜时段，按「[workStart, 23:59:59]」视为工作时段，
     * 简化处理，避免时段归属歧义。
     */
    /**
     * 加班 punch 校验：工作日 vs 休息日/节假日口径不同。
     * - 工作日（NORMAL）：员工上班→正常工作→加班→下班，加班时段内不会有打卡。
     *   只要当天存在至少 1 条打卡，且最新一条打卡时间 ≥ 加班结束时间（下班卡在加班之后）即可。
     * - 休息日/节假日（REST / LEGAL_HOLIDAY）：整天都该在加班，加班时段 [start, end] 内必须有至少 1 条打卡。
     */
    private void validateOvertimePunch(Long employeeId, LocalDate workDate, LocalTime start, LocalTime end, String dayType) {
        LocalDateTime dayStart = workDate.atStartOfDay();
        LocalDateTime dayEnd = workDate.plusDays(1).atStartOfDay();
        List<AttendancePunch> punches = punchMapper.selectList(new LambdaQueryWrapper<AttendancePunch>()
                .eq(AttendancePunch::getEmployeeId, employeeId)
                .ge(AttendancePunch::getPunchTime, dayStart)
                .lt(AttendancePunch::getPunchTime, dayEnd)
                .orderByAsc(AttendancePunch::getPunchTime));
        if (punches == null || punches.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    workDate + " 没有任何打卡记录，无法提交加班申请");
        }

        if ("REST".equals(dayType) || "LEGAL_HOLIDAY".equals(dayType)) {
            // 休息日/节假日：加班时段 [start, end] 内必须有打卡
            LocalDateTime punchFrom = workDate.atTime(start);
            LocalDateTime punchTo = workDate.atTime(end);
            boolean inRange = punches.stream().anyMatch(p ->
                    !p.getPunchTime().isBefore(punchFrom) && !p.getPunchTime().isAfter(punchTo));
            if (!inRange) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        (workDate.getDayOfWeek().getValue() >= 6 ? "周末" : "法定节假日")
                                + "加班时段 " + start + " ~ " + end + " 没有打卡记录，请先在该时段内打卡");
            }
        } else {
            // 工作日：最新打卡时间 ≥ 加班结束时间（下班卡在加班之后）即可
            LocalDateTime lastPunch = punches.get(punches.size() - 1).getPunchTime();
            LocalDateTime endAt = workDate.atTime(end);
            if (lastPunch.isBefore(endAt)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "当天最后打卡时间 " + lastPunch.toLocalTime() + " 早于加班结束 " + end
                                + "，请下班打卡后再提交加班申请");
            }
        }
    }

    /** 同一员工同一日期时段不可重叠：首尾相接（A.end == B.start）不算重叠 */
    private void checkOvertimeOverlap(Long employeeId, LocalDate workDate, LocalTime start, LocalTime end) {
        LocalDateTime newFrom = workDate.atTime(start);
        LocalDateTime newTo = workDate.atTime(end);
        List<Overtime> exist = overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                .eq(Overtime::getEmployeeId, employeeId)
                .eq(Overtime::getWorkDate, workDate)
                .in(Overtime::getStatus, 0, 1)); // 待审批 0 / 已通过 1
        for (Overtime e : exist) {
            if (e.getStartTime() == null || e.getEndTime() == null) continue;
            LocalDateTime existFrom = workDate.atTime(e.getStartTime());
            LocalDateTime existTo = workDate.atTime(e.getEndTime());
            // A.start < B.end && B.start < A.end → 重叠；首尾相接不算
            if (newFrom.isBefore(existTo) && existFrom.isBefore(newTo)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "与 " + e.getStartTime() + " ~ " + e.getEndTime() + " 的加班单时间重叠");
            }
        }
    }

    private int overtimeMinutes(LocalTime start, LocalTime end, String dayType,
                                Employee emp, LocalDate workDate) {
        if ("REST".equals(dayType) || "LEGAL_HOLIDAY".equals(dayType)) {
            return (int) Duration.between(start, end).toMinutes();
        }
        AttendanceRuleService.ResolvedRule resolved = ruleService.resolveRule(emp);
        AttendanceRule rule = resolved == null ? null : resolved.rule();
        if (rule == null || rule.getWorkStart() == null || rule.getWorkEnd() == null) {
            return (int) Duration.between(start, end).toMinutes();
        }
        // 简化跨夜处理：只算「当天 [workStart, 24:00) 」的部分
        LocalTime wStart = rule.getWorkStart();
        LocalTime wEnd = rule.getCrossDay() != null && rule.getCrossDay() == 1
                ? LocalTime.MAX.minusSeconds(1) : rule.getWorkEnd();
        int overlap = overlapMinutes(start, end, wStart, wEnd);
        // 午休也重叠的话要加回来（工作时段本身已包含午休时间，扣了工作就需要再扣一次午休重叠）
        if (rule.getRestStart() != null && rule.getRestEnd() != null) {
            int restOverlap = overlapMinutes(start, end, rule.getRestStart(), rule.getRestEnd());
            // 午休在工作时段内部，overlap 已经包含了午休重叠分钟，实际要扣除的是工作时段减去午休后的有效工作时间
            // 所以：实际工作重叠 = overlap - restOverlap（午休那段本身不算工作时间）
            overlap = overlap - restOverlap;
        }
        // overlap 是加班区间与「当天有效工作时段」的重叠分钟，即这部分不算加班
        long total = Duration.between(start, end).toMinutes();
        return (int) Math.max(0, total - overlap);
    }

    /** 两个半开区间 [a1, a2) 与 [b1, b2) 的重叠分钟数。 */
    private int overlapMinutes(LocalTime a1, LocalTime a2, LocalTime b1, LocalTime b2) {
        LocalTime lo = a1.isAfter(b1) ? a1 : b1;
        LocalTime hi = a2.isBefore(b2) ? a2 : b2;
        if (!hi.isAfter(lo)) {
            return 0;
        }
        return (int) Duration.between(lo, hi).toMinutes();
    }

    public void cancel(Long id) {
        SecurityUser user = currentUser();
        Overtime o = overtimeMapper.selectById(id);
        if (o == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "加班申请不存在");
        }
        if (!user.getAccountId().equals(o.getAccountId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能撤销自己的加班申请");
        }
        if (o.getStatus() == null || o.getStatus() != 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只有待审批的加班可以撤销");
        }
        Long runningId = approvalLauncher.latestRunningId(BIZ_OVERTIME, String.valueOf(id));
        if (runningId != null) {
            ApprovalActionDTO act = new ApprovalActionDTO();
            act.setOperatorId(user.getAccountId());
            act.setOperatorName(user.getUsername());
            act.setNote("申请人撤销加班");
            try {
                com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp =
                        approvalApi.withdraw(runningId, act);
                if (resp == null || !resp.ok()) {
                    throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                            resp == null || resp.getMessage() == null ? "撤回审批单失败" : resp.getMessage());
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("撤回加班审批单失败 instanceId={}", runningId, e);
                throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "撤回审批单失败，请稍后重试");
            }
        }
        overtimeMapper.update(null, new LambdaUpdateWrapper<Overtime>()
                .eq(Overtime::getId, id)
                .eq(Overtime::getStatus, 0)
                .set(Overtime::getStatus, 3));
    }

    /** 我的 / 团队（全部）列表；数据范围始终生效，避免越权 */
    public R.Page<OvertimeVO> list(String scope, Long employeeId, String month, int page, int size) {
        SecurityUser user = currentUser();
        LambdaQueryWrapper<Overtime> w = new LambdaQueryWrapper<>();
        boolean mine = employeeId == null && "mine".equalsIgnoreCase(scope == null ? "mine" : scope);
        if (employeeId != null) {
            Employee emp = requireAccessible(employeeId);
            w.eq(Overtime::getEmployeeId, emp.getId());
        } else if (mine) {
            w.eq(Overtime::getAccountId, user.getAccountId());
        } else {
            List<Long> ids = accessibleEmployees().stream().map(Employee::getId).toList();
            if (ids.isEmpty()) {
                return R.Page.of(List.of(), 0);
            }
            w.in(Overtime::getEmployeeId, ids);
        }
        if (month != null && !month.isBlank()) {
            YearMonth ym = parseMonth(month);
            w.ge(Overtime::getWorkDate, ym.atDay(1)).le(Overtime::getWorkDate, ym.atEndOfMonth());
        }
        w.orderByDesc(Overtime::getWorkDate).orderByDesc(Overtime::getId);
        IPage<Overtime> ip = overtimeMapper.selectPage(new Page<>(page, size), w);
        List<Overtime> records = ip.getRecords();
        Map<Long, String> names = employeeNames(records.stream().map(Overtime::getEmployeeId).collect(Collectors.toSet()));
        List<OvertimeVO> vos = records.stream()
                .map(o -> {
                    OvertimeVO vo = toVO(o, user, false);
                    vo.setEmployeeName(names.getOrDefault(o.getEmployeeId(), ""));
                    // status=0 待审批时反查在途审批实例号，供前端点开详情
                    if (o.getStatus() != null && o.getStatus() == 0) {
                        Long rid = approvalLauncher.latestRunningId(BIZ_OVERTIME, String.valueOf(o.getId()));
                        if (rid == null) {
                            rid = approvalLauncher.latestRunningId(BIZ_OVERTIME_HR, String.valueOf(o.getId()));
                        }
                        vo.setApprovalInstanceId(rid != null ? String.valueOf(rid) : null);
                    }
                    return vo;
                }).toList();
        return R.Page.of(vos, ip.getTotal());
    }

    /* ---------- 审批回调 ---------- */

    public void handleApprovalResult(ApprovalCallbackRequest req) {
        Long id = parseLong(req.getBizId());
        if (id == null) {
            return;
        }
        Overtime o = overtimeMapper.selectById(id);
        if (o == null || o.getStatus() == null || o.getStatus() != 0) {
            log.info("加班回调重复投递或单据不存在，忽略 id={}", id);
            return;
        }
        if (Boolean.TRUE.equals(req.getApproved())) {
            BigDecimal effectiveHours = o.getHours() == null
                    ? BigDecimal.ZERO : o.getHours().setScale(2, RoundingMode.HALF_UP);
            int updated = overtimeMapper.update(null, new LambdaUpdateWrapper<Overtime>()
                    .eq(Overtime::getId, id)
                    .eq(Overtime::getStatus, 0)
                    .set(Overtime::getStatus, 1)
                    .set(Overtime::getSettleStatus, 1)
                    .set(Overtime::getEffectiveHours, effectiveHours)
                    .set(Overtime::getSettledAt, LocalDateTime.now())
                    .set(Overtime::getCalcDetail, "1:1 转调休"));
            if (updated == 0) {
                return;
            }
            grantCompensatory(o, effectiveHours);
        } else {
            int target = req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4) ? 3 : 2;
            overtimeMapper.update(null, new LambdaUpdateWrapper<Overtime>()
                    .eq(Overtime::getId, id)
                    .eq(Overtime::getStatus, 0)
                    .set(Overtime::getStatus, target));
        }
    }

    /**
     * 加班转调休（所有日类型统一 1:1）：每次结算时按当年全部已结算加班统一计算，
     * 避免逐笔累加导致精度漂移（3h/8+4h/8+1h/8 分别四舍五入 ≠ 8h/8 一次性计算）。
     */
    public void grantCompensatory(Overtime o, BigDecimal effectiveHours) {
        if (o.getWorkDate() == null || effectiveHours == null
                || effectiveHours.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        int year = o.getWorkDate().getYear();
        LocalDate periodStart = LocalDate.of(year, 1, 1);
        LocalDate periodEnd = LocalDate.of(year, 12, 31);
        // 查当年所有已结算加班单的 effective_hours 总和（含当前这张）
        List<Overtime> settled = overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                .eq(Overtime::getEmployeeId, o.getEmployeeId())
                .eq(Overtime::getStatus, 1)
                .eq(Overtime::getSettleStatus, 1)
                .ge(Overtime::getWorkDate, periodStart)
                .le(Overtime::getWorkDate, periodEnd));
        BigDecimal totalHours = settled.stream()
                .map(x -> x.getEffectiveHours() == null ? BigDecimal.ZERO : x.getEffectiveHours())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalHours.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal divisor = hoursPerDay == null || hoursPerDay.compareTo(BigDecimal.ZERO) <= 0
                ? new BigDecimal("8") : hoursPerDay;
        // 保留 3 位小数（leave_quota.days 是 DECIMAL(6,3)），统一算不逐笔四舍五入
        BigDecimal recalcDays = totalHours.divide(divisor, 3, RoundingMode.HALF_UP);

        // 找到或创建当年 COMPENSATORY 额度，直接覆盖 total_days（已结算总额的真实映射）
        LeaveQuota q = quotaMapper.selectOne(new LambdaQueryWrapper<LeaveQuota>()
                .eq(LeaveQuota::getEmployeeId, o.getEmployeeId())
                .eq(LeaveQuota::getLeaveType, "COMPENSATORY")
                .eq(LeaveQuota::getPeriodStart, periodStart)
                .last("limit 1"));
        if (q == null) {
            try {
                quotaMapper.insertNaturalYearQuota(IdWorker.getId(), o.getEmployeeId(), "COMPENSATORY",
                        year, periodStart, periodEnd, "加班结算自动创建");
            } catch (DuplicateKeyException e) {
                log.info("COMPENSATORY 额度已被并发创建 employee={} year={}", o.getEmployeeId(), year);
            }
            q = quotaMapper.selectOne(new LambdaQueryWrapper<LeaveQuota>()
                    .eq(LeaveQuota::getEmployeeId, o.getEmployeeId())
                    .eq(LeaveQuota::getLeaveType, "COMPENSATORY")
                    .eq(LeaveQuota::getPeriodStart, periodStart)
                    .last("limit 1"));
        }
        if (q == null) {
            log.error("加班结算找不到/创建不了调休额度 employee={} year={}", o.getEmployeeId(), year);
            return;
        }
        // 差额 = 重算总额 - 当前 total_days（如果之前逐笔加过，差额会修正回来）
        BigDecimal diff = recalcDays.subtract(q.getTotalDays() == null ? BigDecimal.ZERO : q.getTotalDays());
        if (diff.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        quotaService.adjust(q.getId(), diff, null,
                "加班转调休重算 " + totalHours + " 小时 / " + divisor + " = " + recalcDays + " 天（差额 " + diff + "）",
                "OVERTIME_RECALC:" + year);
    }

    /* ---------- 月度汇总 ---------- */

    public List<AttendanceMonthVO> monthly(String month, Long employeeId) {
        YearMonth ym = parseMonth(month);
        if (employeeId != null) {
            return List.of(computeOne(requireAccessible(employeeId), ym));
        }
        return accessibleEmployees().stream().map(emp -> computeOne(emp, ym)).toList();
    }

    public AttendanceMonthVO confirm(String month, Long employeeId) {
        YearMonth ym = parseMonth(month);
        Employee emp = requireAccessible(employeeId);
        AttendanceMonth row = monthMapper.selectOne(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getEmployeeId, emp.getId())
                .eq(AttendanceMonth::getMonth, ym.toString())
                .last("limit 1"));
        if (row != null) {
            if (!Integer.valueOf(1).equals(row.getConfirmed())) {
                monthMapper.update(null, new LambdaUpdateWrapper<AttendanceMonth>()
                        .eq(AttendanceMonth::getId, row.getId())
                        .set(AttendanceMonth::getConfirmed, 1));
                row.setConfirmed(1);
            }
            return toVO(row, emp.getName());
        }
        AttendanceMonth computed = computeRaw(emp, ym);
        computed.setConfirmed(1);
        save(computed, null);
        return toVO(computed, emp.getName());
    }

    /** 解锁已确认的月度考勤；必须填写原因，解锁后立即按日结果重算汇总。 */
    public AttendanceMonthVO unlock(String month, Long employeeId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写解锁原因");
        }
        YearMonth ym = parseMonth(month);
        Employee emp = requireAccessible(employeeId);
        AttendanceMonth row = monthMapper.selectOne(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getEmployeeId, emp.getId())
                .eq(AttendanceMonth::getMonth, ym.toString())
                .last("limit 1"));
        if (row == null || !Integer.valueOf(1).equals(row.getConfirmed())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该月未确认，无需解锁");
        }
        String note = reason.trim();
        if (note.length() > 255) {
            note = note.substring(0, 255);
        }
        monthMapper.update(null, new LambdaUpdateWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getId, row.getId())
                .set(AttendanceMonth::getConfirmed, 0)
                .set(AttendanceMonth::getUnlockReason, note)
                .set(AttendanceMonth::getUnlockedAt, LocalDateTime.now())
                .set(AttendanceMonth::getUnlockedBy, currentUser().getAccountId()));
        dailyService.recalcMonth(emp, ym);
        AttendanceMonth fresh = monthMapper.selectById(row.getId());
        return toVO(fresh == null ? row : fresh, emp.getName());
    }

    private AttendanceMonthVO computeOne(Employee emp, YearMonth ym) {
        AttendanceMonth row = monthMapper.selectOne(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getEmployeeId, emp.getId())
                .eq(AttendanceMonth::getMonth, ym.toString())
                .last("limit 1"));
        // 日结果生成时已用 recalcMonth 维护月汇总，这里优先展示已落库数据，避免被粗算覆盖
        if (row != null) {
            return toVO(row, emp.getName());
        }
        AttendanceMonth computed = computeRaw(emp, ym);
        save(computed, null);
        computed.setConfirmed(0);
        return toVO(computed, emp.getName());
    }

    private AttendanceMonth computeRaw(Employee emp, YearMonth ym) {
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();
        LocalDate actualLast = last.isBefore(today) ? last : today;

        Set<LocalDate> holidays = Set.copyOf(calendarMapper.holidays(first, last));
        Set<LocalDate> extras = Set.copyOf(calendarMapper.extraWorkdays(first, last));
        BigDecimal should = BigDecimal.ZERO;
        BigDecimal pastShould = BigDecimal.ZERO;
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean workday = extras.contains(d) || (!holidays.contains(d) && !weekend);
            if (workday) {
                should = should.add(BigDecimal.ONE);
                if (!d.isAfter(actualLast)) {
                    pastShould = pastShould.add(BigDecimal.ONE);
                }
            }
        }

        BigDecimal leaveDays = BigDecimal.ZERO;
        List<LeaveRequest> leaves = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getEmployeeId, emp.getId())
                .eq(LeaveRequest::getStatus, 1)
                .lt(LeaveRequest::getStartDate, last.plusDays(1))
                .ge(LeaveRequest::getEndDate, first));
        for (LeaveRequest lv : leaves) {
            LocalDate leaveEnd = lv.getEndDate();
            if (leaveEnd.isBefore(first)) {
                continue;
            }
            LocalDate s = lv.getStartDate().isBefore(first) ? first : lv.getStartDate();
            LocalDate e = leaveEnd.isAfter(actualLast) ? actualLast : leaveEnd;
            if (e.isBefore(s)) {
                continue;
            }
            String sp = "AM";
            String ep = "PM";
            if (s.equals(lv.getStartDate())) {
                sp = lv.getStartPeriod() == null ? "AM" : lv.getStartPeriod();
            }
            if (e.equals(lv.getEndDate())) {
                ep = lv.getEndPeriod() == null ? "PM" : lv.getEndPeriod();
            }
            String mode = lv.getDayMode() == null ? "NATURAL" : lv.getDayMode();
            String st = s.equals(lv.getStartDate()) ? lv.getStartTime() : null;
            String et = e.equals(lv.getEndDate()) ? lv.getEndTime() : null;
            LeaveDaysCalculator.Result r = daysCalculator.calculate(s, sp, st, e, ep, et, mode);
            leaveDays = leaveDays.add(r.days());
        }

        BigDecimal overtimeHours = overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                        .eq(Overtime::getEmployeeId, emp.getId())
                        .eq(Overtime::getStatus, 1)
                        .ge(Overtime::getWorkDate, first)
                        .le(Overtime::getWorkDate, last))
                .stream().map(Overtime::getHours).filter(h -> h != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 无 daily 行时按 pastShould - leaveDays 估算，不再直接 should - leaveDays 假满勤
        BigDecimal actual = pastShould.subtract(leaveDays).max(BigDecimal.ZERO);
        AttendanceMonth row = new AttendanceMonth();
        row.setId(IdWorker.getId());
        row.setEmployeeId(emp.getId());
        row.setMonth(ym.toString());
        row.setShouldDays(should.setScale(2, RoundingMode.HALF_UP));
        row.setActualDays(actual.setScale(2, RoundingMode.HALF_UP));
        row.setLeaveDays(leaveDays.setScale(2, RoundingMode.HALF_UP));
        row.setOvertimeHours(overtimeHours.setScale(2, RoundingMode.HALF_UP));
        row.setAbsentDays(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setConfirmed(0);
        return row;
    }

    private void save(AttendanceMonth computed, AttendanceMonth existing) {
        if (existing == null) {
            computed.setCreatedAt(LocalDateTime.now());
            monthMapper.insert(computed);
        } else {
            computed.setId(existing.getId());
            monthMapper.updateById(computed);
        }
    }

    /* ---------- 工具 ---------- */

    private ApprovalStartDTO buildStart(Overtime o, Employee emp, SecurityUser user,
                                        String bizType, BigDecimal monthHours) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(bizType);
        dto.setBizId(String.valueOf(o.getId()));
        dto.setBizNo(String.valueOf(o.getId()));
        dto.setTitle("加班审批 · " + nvl(emp.getName()) + " · " + o.getWorkDate());
        dto.setApplicantId(user.getAccountId());
        dto.setApplicantName(user.getDisplayName());
        dto.setApplicantOrgId(emp.getOrgId());
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(emp.getId()));
        form.put("empNo", nvl(emp.getEmpNo()));
        form.put("name", nvl(emp.getName()));
        form.put("workDate", o.getWorkDate() == null ? null : o.getWorkDate().toString());
        form.put("hours", o.getHours());
        form.put("type", o.getType());
        form.put("typeName", TYPE_NAMES.getOrDefault(o.getType(), o.getType()));
        form.put("monthlyHours", monthHours);
        form.put("reason", nvl(o.getReason()));
        form.put("submitter", nvl(user.getDisplayName()));
        dto.setFormData(form);
        return dto;
    }

    /** 当月已通过加班小时数（不含本次） */
    private BigDecimal approvedMonthHours(Employee emp, LocalDate date) {
        if (date == null) {
            return BigDecimal.ZERO;
        }
        return overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                        .eq(Overtime::getEmployeeId, emp.getId())
                        .eq(Overtime::getStatus, 1)
                        .ge(Overtime::getWorkDate, date.withDayOfMonth(1))
                        .le(Overtime::getWorkDate, date.withDayOfMonth(date.lengthOfMonth())))
                .stream().map(Overtime::getHours).filter(h -> h != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Employee currentEmployee() {
        SecurityUser user = currentUser();
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId())
                .last("limit 1"));
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "当前账号未绑定员工档案，无法提交加班");
        }
        return emp;
    }

    private Employee requireAccessible(Long employeeId) {
        Employee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "员工不存在");
        }
        if (!dataScopeService.canAccess(emp)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该员工的考勤");
        }
        return emp;
    }

    private List<Employee> accessibleEmployees() {
        LambdaQueryWrapper<Employee> w = new LambdaQueryWrapper<>();
        dataScopeService.applyTo(w);
        w.in(Employee::getStatus, 30, 40).orderByAsc(Employee::getEmpNo);
        return employeeMapper.selectList(w);
    }

    private Map<Long, String> employeeNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>().in(Employee::getId, ids))
                .stream().collect(Collectors.toMap(Employee::getId, e -> nvl(e.getName()), (a, b) -> a));
    }

    private OvertimeVO toVO(Overtime o, SecurityUser user, boolean forceMine) {
        OvertimeVO vo = new OvertimeVO();
        vo.setId(o.getId());
        vo.setEmployeeId(o.getEmployeeId());
        Employee emp = employeeMapper.selectById(o.getEmployeeId());
        vo.setEmployeeName(emp == null ? "" : nvl(emp.getName()));
        vo.setWorkDate(o.getWorkDate());
        vo.setStartTime(o.getStartTime());
        vo.setEndTime(o.getEndTime());
        vo.setHours(o.getHours());
        vo.setType(o.getType());
        vo.setTypeName(TYPE_NAMES.getOrDefault(o.getType(), o.getType()));
        vo.setReason(nvl(o.getReason()));
        vo.setStatus(o.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(o.getStatus(), String.valueOf(o.getStatus())));
        vo.setApproveNote(nvl(o.getApproveNote()));
        vo.setCreatedAt(o.getCreatedAt());
        vo.setMine(forceMine || (o.getAccountId() != null && o.getAccountId().equals(user.getAccountId())));
        return vo;
    }

    private AttendanceMonthVO toVO(AttendanceMonth m, String name) {
        AttendanceMonthVO vo = new AttendanceMonthVO();
        vo.setId(m.getId());
        vo.setEmployeeId(m.getEmployeeId());
        vo.setEmployeeName(name);
        vo.setMonth(m.getMonth());
        vo.setShouldDays(m.getShouldDays());
        vo.setActualDays(m.getActualDays());
        vo.setLeaveDays(m.getLeaveDays());
        vo.setOvertimeHours(m.getOvertimeHours());
        vo.setAbsentDays(m.getAbsentDays());
        vo.setLateMinutes(m.getLateMinutes());
        vo.setEarlyMinutes(m.getEarlyMinutes());
        vo.setAbsentHours(m.getAbsentHours());
        vo.setWorkHours(m.getWorkHours());
        vo.setConfirmed(m.getConfirmed());
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

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}

package com.laimengchao.sso.ehr.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.module.attendance.dto.WorkCalendarItem;
import com.laimengchao.sso.ehr.module.attendance.entity.WorkCalendarYear;
import com.laimengchao.sso.ehr.module.attendance.mapper.WorkCalendarYearMapper;
import com.laimengchao.sso.ehr.module.attendance.vo.WorkCalendarVO;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.WorkCalendar;
import com.laimengchao.sso.ehr.module.leave.mapper.WorkCalendarMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.dto.NoticeBizHandledDTO;
import com.laimengchao.sso.settings.client.dto.NoticeSendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作日历维护：数据库只存显式配置，月视图对未配置日期先套用法定节假日/调休预设（2024–2026 年，
 * 依据国务院办公厅历年节假日安排通知），其余按「周一~周五工作日」推算。
 * CSV 导入格式：日期,是否工作日,节假日名称（如 2026-10-01,0,国庆节）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCalendarService {

    /** 待办业务类型：下一年工作日历维护 */
    public static final String BIZ_TYPE_CALENDAR_MAINTAIN = "EHR_CALENDAR_MAINTAIN";
    /** 站内消息规则（settings 侧需配置） */
    private static final String NOTICE_RULE = "ehr.calendar.maintain";
    /** 每年 11 月起允许更新下一年工作日历 */
    private static final int APPLY_OPEN_MONTH = 11;

    private final WorkCalendarMapper calendarMapper;
    private final WorkCalendarYearMapper calendarYearMapper;
    private final OrgNodeMapper orgNodeMapper;
    private final EmployeeMapper employeeMapper;
    private final NoticeApi noticeApi;

    /** HR 组织名称（未配置接收人时的兜底接收部门） */
    @Value("${ehr.notice.hr-org-name:人力资源}")
    private String hrOrgName;
    /** EHR 前端地址，用于待办跳转链接 */
    @Value("${ehr.web-base-url:http://localhost:5179}")
    private String webBaseUrl;

    public List<WorkCalendarVO> month(String month) {
        YearMonth ym = parseMonth(month);
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        Map<LocalDate, WorkCalendar> configured = new HashMap<>();
        for (WorkCalendar c : calendarMapper.selectList(new LambdaQueryWrapper<WorkCalendar>()
                .ge(WorkCalendar::getWorkDate, first)
                .le(WorkCalendar::getWorkDate, last))) {
            configured.put(c.getWorkDate(), c);
        }
        List<WorkCalendarVO> rows = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            WorkCalendar c = configured.get(d);
            WorkCalendarVO vo = new WorkCalendarVO();
            vo.setWorkDate(d);
            vo.setIsConfigured(c != null);
            if (c != null) {
                vo.setIsWorkday(c.getIsWorkday());
                vo.setHolidayName(c.getHolidayName() == null ? "" : c.getHolidayName());
                vo.setSource("MANUAL");
            } else {
                String holiday = AttendanceHolidayPreset.holidayName(d);
                if (holiday != null) {
                    vo.setIsWorkday(0);
                    vo.setHolidayName(holiday);
                    vo.setSource("PRESET");
                } else if (AttendanceHolidayPreset.isMakeupWorkday(d)) {
                    vo.setIsWorkday(1);
                    vo.setHolidayName(AttendanceHolidayPreset.makeupName(d));
                    vo.setSource("PRESET");
                } else {
                    DayOfWeek dow = d.getDayOfWeek();
                    boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
                    vo.setIsWorkday(weekend ? 0 : 1);
                    vo.setHolidayName("");
                    vo.setSource("DEFAULT");
                }
            }
            rows.add(vo);
        }
        return rows;
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(List<WorkCalendarItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (WorkCalendarItem item : items) {
            if (item.getWorkDate() == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "日期不能为空");
            }
            if (item.getWorkDate().isBefore(today)) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "已过去的日期不允许调整工作日历：" + item.getWorkDate());
            }
            WorkCalendar row = new WorkCalendar();
            row.setWorkDate(item.getWorkDate());
            row.setIsWorkday(item.getIsWorkday() == null ? 1 : item.getIsWorkday());
            row.setHolidayName(item.getHolidayName() == null ? "" : item.getHolidayName().trim());
            WorkCalendar existing = calendarMapper.selectById(item.getWorkDate());
            if (existing == null) {
                calendarMapper.insert(row);
            } else {
                calendarMapper.updateById(row);
            }
        }
    }

    /** 日类型：WORKDAY 工作日 / REST 休息日 / LEGAL_HOLIDAY 法定节假日。 */
    public String resolveDayType(LocalDate date) {
        WorkCalendar cal = calendarMapper.selectById(date);
        if (cal != null) {
            if (Integer.valueOf(0).equals(cal.getIsWorkday())) {
                boolean namedHoliday = cal.getHolidayName() != null && !cal.getHolidayName().isBlank();
                return namedHoliday ? "LEGAL_HOLIDAY" : "REST";
            }
            return "WORKDAY";
        }
        if (AttendanceHolidayPreset.holidayName(date) != null) {
            return "LEGAL_HOLIDAY";
        }
        if (AttendanceHolidayPreset.isMakeupWorkday(date)) {
            return "WORKDAY";
        }
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY ? "REST" : "WORKDAY";
    }

    /**
     * CSV 批量导入工作日历；首行可为表头。日期支持 yyyy-MM-dd，也支持 2026/10/1。
     * 是否工作日接受 1/0、是/否、工作日/休息、Y/N。
     *
     * @return 成功写入的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请上传 CSV 文件");
        }
        List<WorkCalendarItem> items = new ArrayList<>();
        Set<LocalDate> seen = new HashSet<>();
        int lineNo = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String text = lineNo == 1 ? line.replace("\uFEFF", "") : line;
                if (text.isBlank()) {
                    continue;
                }
                String[] parts = text.split(",", -1);
                String dateText = parts[0].trim();
                if (lineNo == 1 && isHeader(dateText)) {
                    continue;
                }
                LocalDate date = parseDate(dateText, lineNo);
                if (!seen.add(date)) {
                    continue;
                }
                WorkCalendarItem item = new WorkCalendarItem();
                item.setWorkDate(date);
                item.setIsWorkday(parseWorkday(parts.length > 1 ? parts[1].trim() : "", lineNo));
                item.setHolidayName(parts.length > 2 ? parts[2].trim() : "");
                items.add(item);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("工作日历 CSV 解析失败", e);
            throw new BizException(ErrorCode.PARAM_ERROR, "CSV 解析失败，请检查文件格式");
        }
        save(items);
        return items.size();
    }

    private static boolean isHeader(String text) {
        return text.contains("日期") || "date".equalsIgnoreCase(text);
    }

    private static LocalDate parseDate(String text, int lineNo) {
        try {
            return LocalDate.parse(text.replace('/', '-'));
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "第 " + lineNo + " 行日期格式不正确：" + text);
        }
    }

    private static int parseWorkday(String text, int lineNo) {
        String t = text.toLowerCase();
        if (t.isEmpty() || "1".equals(t) || "是".equals(text) || "y".equals(t)
                || "workday".equals(t) || "工作日".equals(text)) {
            return 1;
        }
        if ("0".equals(t) || "否".equals(text) || "n".equals(t)
                || "rest".equals(t) || "holiday".equals(t) || "休息".equals(text) || "节假日".equals(text)) {
            return 0;
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "第 " + lineNo + " 行是否工作日只能是 1/0 或 是/否");
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }

    // ==================== 下一年工作日历一键更新 ====================

    /**
     * 下一年工作日历状态：每年 11 月起，若下一年尚未更新则前端展示「更新下一年工作日历」按钮。
     */
    public Map<String, Object> nextYearStatus() {
        LocalDate today = LocalDate.now();
        int nextYear = today.getYear() + 1;
        WorkCalendarYear record = calendarYearMapper.selectById(nextYear);
        Map<String, Object> out = new HashMap<>();
        out.put("nextYear", nextYear);
        out.put("applied", record != null);
        out.put("appliedAt", record == null ? null : record.getAppliedAt());
        out.put("appliedBy", record == null ? "" : record.getAppliedBy());
        out.put("showButton", today.getMonthValue() >= APPLY_OPEN_MONTH && record == null);
        out.put("holidayCount", AttendanceHolidayPreset.holidaysOf(nextYear).size());
        out.put("makeupCount", AttendanceHolidayPreset.makeupsOf(nextYear).size());
        return out;
    }

    /**
     * 一键更新下一年工作日历：把该年度的法定节假日与调休上班日预设写入工作日历（不覆盖已有显式配置），
     * 记录年度更新标记，并关闭相关维护待办（按钮随之隐藏）。
     */
    @Transactional
    public Map<String, Object> applyNextYear(String operator) {
        LocalDate today = LocalDate.now();
        int nextYear = today.getYear() + 1;
        if (today.getMonthValue() < APPLY_OPEN_MONTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "每年 11 月起才开放更新下一年工作日历");
        }
        if (calendarYearMapper.selectById(nextYear) != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, nextYear + " 年工作日历已更新，无需重复操作");
        }
        Set<LocalDate> existing = new HashSet<>();
        LocalDate first = LocalDate.of(nextYear, 1, 1);
        LocalDate last = LocalDate.of(nextYear, 12, 31);
        for (WorkCalendar c : calendarMapper.selectList(new LambdaQueryWrapper<WorkCalendar>()
                .ge(WorkCalendar::getWorkDate, first).le(WorkCalendar::getWorkDate, last))) {
            existing.add(c.getWorkDate());
        }
        int inserted = 0;
        for (Map.Entry<LocalDate, String> e : AttendanceHolidayPreset.holidaysOf(nextYear).entrySet()) {
            if (!existing.contains(e.getKey())) {
                WorkCalendar row = new WorkCalendar();
                row.setWorkDate(e.getKey());
                row.setIsWorkday(0);
                row.setHolidayName(e.getValue());
                calendarMapper.insert(row);
                inserted++;
            }
        }
        for (Map.Entry<LocalDate, String> e : AttendanceHolidayPreset.makeupsOf(nextYear).entrySet()) {
            if (!existing.contains(e.getKey())) {
                WorkCalendar row = new WorkCalendar();
                row.setWorkDate(e.getKey());
                row.setIsWorkday(1);
                row.setHolidayName(e.getValue());
                calendarMapper.insert(row);
                inserted++;
            }
        }
        WorkCalendarYear record = new WorkCalendarYear();
        record.setCalYear(nextYear);
        record.setAppliedAt(java.time.LocalDateTime.now());
        record.setAppliedBy(operator == null ? "" : operator);
        calendarYearMapper.insert(record);
        // 关闭该年度的维护待办
        try {
            NoticeBizHandledDTO handled = new NoticeBizHandledDTO();
            handled.setBizType(BIZ_TYPE_CALENDAR_MAINTAIN);
            handled.setBizId(String.valueOf(nextYear));
            noticeApi.bizHandled(handled);
        } catch (Exception e) {
            log.warn("关闭工作日历维护待办失败: {}", e.getMessage());
        }
        Map<String, Object> out = new HashMap<>();
        out.put("nextYear", nextYear);
        out.put("inserted", inserted);
        out.put("skipped", (AttendanceHolidayPreset.holidaysOf(nextYear).size()
                + AttendanceHolidayPreset.makeupsOf(nextYear).size()) - inserted);
        return out;
    }

    /**
     * 每日提醒：11 月起若下一年工作日历尚未更新，向指定接收人（或 HR 部门）发送维护待办。
     * 完成后调用方通过 bizHandled 关闭；同一年度同一天重复发送由 settings 侧按业务去重。
     */
    public void remindNextYearCalendarIfNeeded() {
        LocalDate today = LocalDate.now();
        if (today.getMonthValue() < APPLY_OPEN_MONTH) {
            return;
        }
        int nextYear = today.getYear() + 1;
        if (calendarYearMapper.selectById(nextYear) != null) {
            return;
        }
        List<Long> receivers = resolveMaintainerAccountIds();
        if (receivers.isEmpty()) {
            log.warn("工作日历维护待办无接收人（未配置 ehr.attendance.calendar-maintainer-ids 且 HR 部门无账号）");
            return;
        }
        try {
            NoticeSendDTO dto = new NoticeSendDTO();
            dto.setRuleCode(NOTICE_RULE);
            dto.setChannel(1);
            dto.setBizType(BIZ_TYPE_CALENDAR_MAINTAIN);
            dto.setBizId(String.valueOf(nextYear));
            dto.setTitle("请更新 " + nextYear + " 年工作日历");
            dto.setContent(nextYear + " 年法定节假日安排已发布，请在考勤管理-工作日历页点击「更新 "
                    + nextYear + " 年工作日历」完成维护；节假日配置将影响考勤与请假计算。");
            dto.setLink(webBaseUrl + "/attendance?tab=calendar");
            dto.setAccountIds(receivers);
            noticeApi.sendBatch(dto);
            log.info("工作日历维护待办已发送：year={}, receivers={}", nextYear, receivers.size());
        } catch (Exception e) {
            log.error("发送工作日历维护待办失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 接收人：取配置中心消息规则 ehr.calendar.maintain 上维护的指定接收人
     * （账号 + 部门，部门含下级，可混合）；规则未配置接收人时兜底发 HR 部门（含子级）全体在职员工。
     */
    private List<Long> resolveMaintainerAccountIds() {
        Set<Long> accountIds = new LinkedHashSet<>();
        List<Long> ruleAccounts = new ArrayList<>();
        List<Long> ruleOrgs = new ArrayList<>();
        try {
            var res = noticeApi.ruleReceivers(NOTICE_RULE);
            Map<String, List<Long>> receivers = res == null ? null : res.getData();
            if (receivers != null) {
                if (receivers.get("accountIds") != null) {
                    ruleAccounts.addAll(receivers.get("accountIds"));
                }
                if (receivers.get("orgIds") != null) {
                    ruleOrgs.addAll(receivers.get("orgIds"));
                }
            }
        } catch (Exception e) {
            log.warn("读取规则接收人配置失败，按未配置处理: {}", e.getMessage());
        }
        accountIds.addAll(ruleAccounts);
        if (!ruleOrgs.isEmpty()) {
            Set<Long> orgIds = new HashSet<>();
            for (Long deptId : ruleOrgs) {
                collectSubtreeOrgIds(deptId, orgIds);
            }
            if (!orgIds.isEmpty()) {
                for (Employee emp : employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                        .in(Employee::getOrgId, orgIds)
                        .in(Employee::getStatus, 30, 40)
                        .isNotNull(Employee::getAccountId))) {
                    accountIds.add(emp.getAccountId());
                }
            }
            return new ArrayList<>(accountIds);
        }
        if (!accountIds.isEmpty()) {
            return new ArrayList<>(accountIds);
        }
        // 规则未配置接收人：兜底 HR 部门
        List<OrgNode> roots = orgNodeMapper.selectList(new LambdaQueryWrapper<OrgNode>()
                .like(OrgNode::getName, hrOrgName).eq(OrgNode::getType, "DEPARTMENT"));
        if (roots.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> orgIds = new HashSet<>();
        for (OrgNode root : roots) {
            collectSubtreeOrgIds(root.getId(), orgIds);
        }
        for (Employee emp : employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getOrgId, orgIds)
                .in(Employee::getStatus, 30, 40)
                .isNotNull(Employee::getAccountId))) {
            accountIds.add(emp.getAccountId());
        }
        return new ArrayList<>(accountIds);
    }

    /** 收集某组织节点及其全部子级节点 ID（按 path 前缀匹配）。 */
    private void collectSubtreeOrgIds(Long rootId, Set<Long> out) {
        OrgNode root = orgNodeMapper.selectById(rootId);
        if (root == null) {
            return;
        }
        out.add(root.getId());
        for (OrgNode n : orgNodeMapper.selectList(new LambdaQueryWrapper<OrgNode>()
                .likeRight(OrgNode::getPath, root.getPath()))) {
            out.add(n.getId());
        }
    }
}

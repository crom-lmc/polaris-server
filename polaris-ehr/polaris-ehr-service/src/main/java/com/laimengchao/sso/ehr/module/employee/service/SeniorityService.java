package com.laimengchao.sso.ehr.module.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.dto.SeniorityConfirmRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeSeniorityRecord;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeSenioritySegment;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeSeniorityRecordMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeSenioritySegmentMapper;
import com.laimengchao.sso.ehr.module.employee.vo.SeniorityVO;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 司龄核定与日常刷新。
 * <p>
 * 司龄用于年假档位：本单位司龄看 seniority_start_date，累计工龄 = 前司年限 + 本单位司龄。
 * 前司年限由「多段工作经历」逐段加总（可断档，重叠区间只算一次），每段必须附证明。
 * 闰年简化：统一按 365.25 天/年折算，向下保留 1 位小数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeniorityService {

    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365.25");

    private final EmployeeMapper employeeMapper;
    private final EmployeeSeniorityRecordMapper recordMapper;
    private final EmployeeSenioritySegmentMapper segmentMapper;
    private final OrgNodeMapper orgMapper;

    /** 司龄列表：keyword 匹配姓名/工号；employeeId 可选精确筛选 */
    public R.Page<SeniorityVO> page(String keyword, Long employeeId, int page, int size) {
        LambdaQueryWrapper<Employee> w = new LambdaQueryWrapper<>();
        if (employeeId != null) {
            w.eq(Employee::getId, employeeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            w.and(q -> q.like(Employee::getName, k).or().like(Employee::getEmpNo, k));
        }
        // 已离职人员不再维护司龄
        w.ne(Employee::getStatus, EmployeeStatus.LEFT.getCode());
        w.orderByAsc(Employee::getEmpNo);
        IPage<Employee> ip = employeeMapper.selectPage(new Page<>(page, size), w);
        if (ip.getRecords().isEmpty()) {
            return R.Page.of(List.of(), ip.getTotal());
        }

        Map<Long, String> orgNames = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgNames.put(n.getId(), n.getName() == null ? "" : n.getName());
        }
        List<Long> empIds = ip.getRecords().stream().map(Employee::getId).toList();
        // 一次取出这些员工的有效记录，按生效日倒序，每人取最新一条回显
        Map<Long, EmployeeSeniorityRecord> latest = new HashMap<>();
        for (EmployeeSeniorityRecord r : recordMapper.selectList(new LambdaQueryWrapper<EmployeeSeniorityRecord>()
                .in(EmployeeSeniorityRecord::getEmployeeId, empIds)
                .orderByDesc(EmployeeSeniorityRecord::getEffectiveDate)
                .orderByDesc(EmployeeSeniorityRecord::getId))) {
            latest.putIfAbsent(r.getEmployeeId(), r);
        }
        List<SeniorityVO> rows = ip.getRecords().stream()
                .map(e -> toVO(e, orgNames.get(e.getOrgId()), latest.get(e.getId())))
                .collect(Collectors.toList());
        return R.Page.of(rows, ip.getTotal());
    }

    /** 某员工的司龄核定历史（生效日倒序） */
    public List<EmployeeSeniorityRecord> records(Long employeeId) {
        requireEmployee(employeeId);
        return recordMapper.selectList(new LambdaQueryWrapper<EmployeeSeniorityRecord>()
                .eq(EmployeeSeniorityRecord::getEmployeeId, employeeId)
                .orderByDesc(EmployeeSeniorityRecord::getEffectiveDate)
                .orderByDesc(EmployeeSeniorityRecord::getId));
    }

    /** 某员工最近一次核定的「前司工作段」（用于核定弹窗回显、可继续增删） */
    public List<EmployeeSenioritySegment> latestSegments(Long employeeId) {
        EmployeeSeniorityRecord latest = latestRecord(employeeId);
        if (latest == null) {
            return List.of();
        }
        return segmentMapper.selectList(new LambdaQueryWrapper<EmployeeSenioritySegment>()
                .eq(EmployeeSenioritySegment::getEmployeeId, employeeId)
                .eq(EmployeeSenioritySegment::getRecordId, latest.getId())
                .orderByAsc(EmployeeSenioritySegment::getStartDate)
                .orderByAsc(EmployeeSenioritySegment::getId));
    }

    /**
     * 核定司龄：追加一条核定记录，并回写员工档案上的司龄起算日 / 首次工作日期 / 当前司龄。
     * <p>
     * 传了 segments 时：前司年限 = 各段合并后的天数合计（断档不计、重叠只算一次），
     * 首次参加工作日期 = 最早一段的开始日；没传 segments 时沿用旧的手工前司年限。
     */
    @Transactional(rollbackFor = Exception.class)
    public SeniorityVO confirm(Long employeeId, SeniorityConfirmRequest req) {
        Employee emp = requireEmployee(employeeId);
        // 司龄只允许核定一次：已核定过的员工不允许再次提交（避免历史多版本、工龄口径漂移）
        Long confirmed = recordMapper.selectCount(new LambdaQueryWrapper<EmployeeSeniorityRecord>()
                .eq(EmployeeSeniorityRecord::getEmployeeId, employeeId));
        if (confirmed != null && confirmed > 0) {
            throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID, "该员工司龄已核定，不能重复核定");
        }
        LocalDate effective = req.getEffectiveDate() == null ? LocalDate.now() : req.getEffectiveDate();
        List<SeniorityConfirmRequest.Segment> segments = normalizeSegments(req.getSegments());
        LocalDate firstWork = segments.isEmpty()
                ? req.getFirstWorkDate()
                : segments.stream().map(SeniorityConfirmRequest.Segment::getStartDate)
                        .min(Comparator.naturalOrder()).orElse(req.getFirstWorkDate());
        BigDecimal prior = segments.isEmpty() ? nvl(req.getPriorYears()) : segmentYears(segments);
        // 累计工龄没传时，按「前司年限 + 本单位从起算日到生效日」计算
        BigDecimal total = req.getTotalYears() != null
                ? req.getTotalYears().setScale(1, RoundingMode.DOWN)
                : prior.add(yearsBetween(req.getSeniorityStartDate(), effective)).setScale(1, RoundingMode.DOWN);

        SecurityUser user = SecurityUtils.currentUser().orElseThrow(() ->
                new BizException(ErrorCode.UNAUTHORIZED));
        EmployeeSeniorityRecord record = new EmployeeSeniorityRecord();
        record.setEmployeeId(employeeId);
        record.setEffectiveDate(effective);
        record.setSeniorityStartDate(req.getSeniorityStartDate());
        record.setFirstWorkDate(firstWork);
        record.setPriorYears(prior);
        record.setTotalYears(total);
        record.setSource(req.getSource() == null || req.getSource().isBlank()
                ? "MANUAL" : req.getSource().trim().toUpperCase());
        record.setProofFileId(req.getProofFileId());
        record.setRemark(req.getRemark() == null ? "" : req.getRemark().trim());
        record.setConfirmedBy(user.getAccountId());
        record.setConfirmedAt(LocalDateTime.now());
        recordMapper.insert(record);

        for (SeniorityConfirmRequest.Segment s : segments) {
            EmployeeSenioritySegment seg = new EmployeeSenioritySegment();
            seg.setEmployeeId(employeeId);
            seg.setRecordId(record.getId());
            seg.setCompany(s.getCompany() == null ? "" : s.getCompany().trim());
            seg.setStartDate(s.getStartDate());
            seg.setEndDate(s.getEndDate());
            seg.setProofFileId(s.getProofFileId());
            seg.setProofFileName(s.getProofFileName() == null ? "" : s.getProofFileName().trim());
            seg.setRemark(s.getRemark() == null ? "" : s.getRemark().trim());
            segmentMapper.insert(seg);
        }

        BigDecimal current = yearsBetween(req.getSeniorityStartDate(), LocalDate.now());
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, employeeId)
                .set(Employee::getSeniorityStartDate, req.getSeniorityStartDate())
                .set(Employee::getFirstWorkDate, firstWork)
                .set(Employee::getSeniorityYears, current)
                .set(Employee::getSeniorityConfirmedAt, record.getConfirmedAt())
                .set(Employee::getSeniorityConfirmedBy, user.getAccountId()));
        emp.setSeniorityStartDate(req.getSeniorityStartDate());
        emp.setFirstWorkDate(firstWork);
        emp.setSeniorityYears(current);
        emp.setSeniorityConfirmedAt(record.getConfirmedAt());
        emp.setSeniorityConfirmedBy(user.getAccountId());
        log.info("司龄核定完成 employeeId={} 起算日={} 首次工作={} 前司年限={} 累计工龄={} 工作段={}",
                employeeId, req.getSeniorityStartDate(), firstWork, prior, total, segments.size());
        return toVO(emp, null, record);
    }

    /** 校验工作段：起止日期合法、每段必须有证明附件；丢掉空行 */
    private List<SeniorityConfirmRequest.Segment> normalizeSegments(List<SeniorityConfirmRequest.Segment> raw) {
        List<SeniorityConfirmRequest.Segment> valid = new ArrayList<>();
        if (raw == null) {
            return valid;
        }
        for (SeniorityConfirmRequest.Segment s : raw) {
            if (s == null) {
                continue;
            }
            boolean blank = s.getStartDate() == null && s.getEndDate() == null && s.getProofFileId() == null
                    && (s.getCompany() == null || s.getCompany().isBlank());
            if (blank) {
                continue;
            }
            int no = valid.size() + 1;
            if (s.getStartDate() == null || s.getEndDate() == null) {
                throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID, "第 " + no + " 段工作经历请补全起止日期");
            }
            if (s.getEndDate().isBefore(s.getStartDate())) {
                throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID, "第 " + no + " 段工作经历的结束日期不能早于开始日期");
            }
            if (s.getProofFileId() == null) {
                throw new BizException(EhrErrorCode.EMP_PROFILE_INVALID, "第 " + no + " 段工作经历请上传证明附件");
            }
            valid.add(s);
        }
        return valid;
    }

    /**
     * 多段工作经历合并后累计年限：按开始日期排序，相邻/重叠区间合并，
     * 断档部分不计，最后按 365.25 天/年折算（含首尾两天）。
     */
    private BigDecimal segmentYears(List<SeniorityConfirmRequest.Segment> segments) {
        List<SeniorityConfirmRequest.Segment> sorted = segments.stream()
                .sorted(Comparator.comparing(SeniorityConfirmRequest.Segment::getStartDate))
                .toList();
        long days = 0;
        LocalDate start = null;
        LocalDate end = null;
        for (SeniorityConfirmRequest.Segment s : sorted) {
            if (start == null) {
                start = s.getStartDate();
                end = s.getEndDate();
            } else if (!s.getStartDate().isAfter(end.plusDays(1))) {
                if (s.getEndDate().isAfter(end)) {
                    end = s.getEndDate();
                }
            } else {
                days += ChronoUnit.DAYS.between(start, end) + 1;
                start = s.getStartDate();
                end = s.getEndDate();
            }
        }
        if (start != null) {
            days += ChronoUnit.DAYS.between(start, end) + 1;
        }
        return BigDecimal.valueOf(days).divide(DAYS_PER_YEAR, 1, RoundingMode.DOWN);
    }

    private EmployeeSeniorityRecord latestRecord(Long employeeId) {
        return recordMapper.selectOne(new LambdaQueryWrapper<EmployeeSeniorityRecord>()
                .eq(EmployeeSeniorityRecord::getEmployeeId, employeeId)
                .orderByDesc(EmployeeSeniorityRecord::getEffectiveDate)
                .orderByDesc(EmployeeSeniorityRecord::getId)
                .last("limit 1"));
    }

    /** 天数 → 年，按 365.25 天/年向下保留 1 位 */
    public static BigDecimal yearsBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.DOWN);
        }
        long days = ChronoUnit.DAYS.between(from, to);
        return BigDecimal.valueOf(days).divide(DAYS_PER_YEAR, 1, RoundingMode.DOWN);
    }

    private Employee requireEmployee(Long employeeId) {
        Employee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "员工不存在");
        }
        return emp;
    }

    private SeniorityVO toVO(Employee e, String orgName, EmployeeSeniorityRecord latest) {
        SeniorityVO vo = new SeniorityVO();
        vo.setEmployeeId(e.getId());
        vo.setEmpNo(e.getEmpNo());
        vo.setName(e.getName());
        vo.setOrgId(e.getOrgId());
        vo.setOrgName(orgName == null ? "" : orgName);
        vo.setHireDate(e.getHireDate());
        vo.setSeniorityStartDate(e.getSeniorityStartDate());
        vo.setFirstWorkDate(e.getFirstWorkDate());
        vo.setSeniorityYears(e.getSeniorityYears() == null ? BigDecimal.ZERO : e.getSeniorityYears());
        if (latest != null) {
            vo.setPriorYears(latest.getPriorYears());
            vo.setTotalYears(latest.getTotalYears());
            vo.setSource(latest.getSource());
            vo.setConfirmedAt(latest.getConfirmedAt());
        } else {
            vo.setPriorYears(BigDecimal.ZERO);
            vo.setTotalYears(BigDecimal.ZERO);
            vo.setSource("");
        }
        return vo;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

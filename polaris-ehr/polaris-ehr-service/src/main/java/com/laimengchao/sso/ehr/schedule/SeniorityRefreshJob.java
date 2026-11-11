package com.laimengchao.sso.ehr.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeSeniorityRecord;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeSeniorityRecordMapper;
import com.laimengchao.sso.ehr.module.employee.service.SeniorityService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 司龄与累计工龄每日刷新。
 * <p>
 * 在岗人员（试用期/在职/离职中/待关闭账号）每天按「司龄起算日 → 今天」重算本单位司龄，
 * 并用「前司年限 + 本单位司龄」刷新最近一条核定记录的累计工龄；已离职员工冻结不再增长。
 * 触发由调度中心配置（cron 0 0 2 * * ?）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeniorityRefreshJob implements JobHandler {

    private final EmployeeMapper employeeMapper;
    private final EmployeeSeniorityRecordMapper recordMapper;

    @Override
    public String name() {
        return "seniorityRefresh";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getStatus, EmployeeStatus.PROBATION.getCode(), EmployeeStatus.ACTIVE.getCode(),
                        EmployeeStatus.OFFBOARDING.getCode(), EmployeeStatus.PENDING_ACCOUNT_CLOSE.getCode()));
        int changed = 0;
        for (Employee e : employees) {
            LocalDate start = e.getSeniorityStartDate() != null ? e.getSeniorityStartDate() : e.getHireDate();
            if (start == null) {
                continue;
            }
            BigDecimal years = SeniorityService.yearsBetween(start, LocalDate.now());
            if (e.getSeniorityYears() == null || e.getSeniorityYears().compareTo(years) != 0) {
                Employee upd = new Employee();
                upd.setId(e.getId());
                upd.setSeniorityYears(years);
                employeeMapper.updateById(upd);
                changed++;
            }
            // 累计工龄 = 前司年限 + 本单位司龄，同步回最近一条核定记录，台账/档案显示才一致
            EmployeeSeniorityRecord latest = recordMapper.selectOne(new LambdaQueryWrapper<EmployeeSeniorityRecord>()
                    .eq(EmployeeSeniorityRecord::getEmployeeId, e.getId())
                    .orderByDesc(EmployeeSeniorityRecord::getEffectiveDate)
                    .orderByDesc(EmployeeSeniorityRecord::getId)
                    .last("limit 1"));
            if (latest == null) {
                continue;
            }
            BigDecimal prior = latest.getPriorYears() == null ? BigDecimal.ZERO : latest.getPriorYears();
            BigDecimal total = prior.add(years).setScale(1, RoundingMode.DOWN);
            if (latest.getTotalYears() == null || latest.getTotalYears().compareTo(total) != 0) {
                EmployeeSeniorityRecord recUpd = new EmployeeSeniorityRecord();
                recUpd.setId(latest.getId());
                recUpd.setTotalYears(total);
                recordMapper.updateById(recUpd);
                changed++;
            }
        }
        log.info("每日司龄/工龄刷新完成，共 {} 人，更新 {} 处", employees.size(), changed);
        return JobResult.ok("共 " + employees.size() + " 人，更新 " + changed + " 处");
    }
}

package com.laimengchao.sso.ehr.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveCarryoverWaiver;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveCarryoverWaiverMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveQuotaMapper;
import com.laimengchao.sso.ehr.module.leave.service.AnnualLeaveService;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.dto.NoticeSendDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 12 月结转年假到期待办：当年额度里「上年结转」仍有剩余的员工，每天 09:30 发待办提醒，
 * 直到员工签署自动放弃（ehr_leave_carryover_waiver）或结转休完；12-31 后自然停止。
 * 待办点击跳转「我的假期」页，页面上可直接签署放弃。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnualLeaveCarryoverNoticeJob {

    private final LeaveQuotaMapper quotaMapper;
    private final LeaveCarryoverWaiverMapper waiverMapper;
    private final EmployeeMapper employeeMapper;
    private final NoticeApi noticeApi;

    /** EHR 前端地址：待办点击跳「我的假期」页 */
    @Value("${ehr.web-base-url:http://127.0.0.1:5179}")
    private String webBaseUrl;

    @Scheduled(cron = "0 30 9 * 12 ?", zone = "Asia/Shanghai")
    public void remindCarryoverExpiring() {
        int year = LocalDate.now().getYear();
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getStatus, EmployeeStatus.PROBATION.getCode(), EmployeeStatus.ACTIVE.getCode(),
                        EmployeeStatus.OFFBOARDING.getCode(), EmployeeStatus.PENDING_ACCOUNT_CLOSE.getCode())
                .isNotNull(Employee::getAccountId));
        int sent = 0;
        for (Employee emp : employees) {
            try {
                LeaveQuota q = quotaMapper.selectOne(new LambdaQueryWrapper<LeaveQuota>()
                        .eq(LeaveQuota::getEmployeeId, emp.getId())
                        .eq(LeaveQuota::getLeaveType, AnnualLeaveService.LEAVE_TYPE_ANNUAL)
                        .eq(LeaveQuota::getPeriodStart, LocalDate.of(year, 1, 1))
                        .last("limit 1"));
                if (q == null) {
                    continue;
                }
                BigDecimal remaining = nvl(q.getCarryoverIn()).subtract(nvl(q.getCarryoverUsed()));
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Long waived = waiverMapper.selectCount(new LambdaQueryWrapper<LeaveCarryoverWaiver>()
                        .eq(LeaveCarryoverWaiver::getEmployeeId, emp.getId())
                        .eq(LeaveCarryoverWaiver::getYear, year));
                if (waived != null && waived > 0) {
                    continue;
                }
                NoticeSendDTO dto = new NoticeSendDTO();
                dto.setRuleCode("ehr.leave.carryover.expire");
                // 强制站内方式为「待办」，否则规则缺失时会退化成普通消息
                dto.setChannel(1);
                dto.setAccountIds(List.of(emp.getAccountId()));
                dto.setTitle("待办：上年结转年假即将到期");
                dto.setContent("你还有 " + remaining.stripTrailingZeros().toPlainString()
                        + " 天上年结转的年假未休完，将于 " + year + "-12-31 到期作废。"
                        + "请及时安排休假，或在「我的假期」页签署自动放弃。");
                dto.setBizType("LEAVE_CARRYOVER_EXPIRE");
                dto.setBizId(String.valueOf(emp.getId()));
                dto.setLink(webBaseUrl + "/leaves");
                noticeApi.sendBatch(dto);
                sent++;
            } catch (Exception e) {
                // 单个员工待办失败不阻断其他人，明天还会再发
                log.error("结转到期待办发送失败 employeeId={}", emp.getId(), e);
            }
        }
        log.info("结转年假到期待办发送完成 year={} 发送 {} 人", year, sent);
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}

package com.laimengchao.sso.job.module.job.controller;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.job.constant.PermissionCodes;
import com.laimengchao.sso.job.module.job.dto.AlarmChannelRequest;
import com.laimengchao.sso.job.module.job.dto.AlarmRuleRequest;
import com.laimengchao.sso.job.module.job.entity.JobAlarm;
import com.laimengchao.sso.job.module.job.entity.JobAlarmChannel;
import com.laimengchao.sso.job.module.job.service.JobAlarmService;
import com.laimengchao.sso.job.module.job.vo.AlarmRecordVO;
import com.laimengchao.sso.job.security.Permit;
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

/** 报警配置：全局通知渠道、报警规则、报警流水 */
@RestController
@RequestMapping("/api/jobs/alarms")
public class AdminAlarmController {

    private final JobAlarmService alarmService;

    public AdminAlarmController(JobAlarmService alarmService) {
        this.alarmService = alarmService;
    }

    @GetMapping("/channels")
    @Permit(PermissionCodes.JOB_VIEW)
    public R<List<JobAlarmChannel>> channels() {
        return R.ok(alarmService.listChannels());
    }

    @PutMapping("/channels")
    @Permit(PermissionCodes.JOB_MANAGE)
    public R<Void> saveChannels(@RequestBody List<AlarmChannelRequest> items) {
        alarmService.saveChannels(items);
        return R.ok();
    }

    @GetMapping("/rules")
    @Permit(PermissionCodes.JOB_VIEW)
    public R<PageResult<JobAlarm>> rules(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size) {
        return R.ok(alarmService.listRules(page, size));
    }

    @PostMapping("/rules")
    @Permit(PermissionCodes.JOB_MANAGE)
    public R<JobAlarm> createRule(@RequestBody AlarmRuleRequest req) {
        return R.ok(alarmService.saveRule(null, req));
    }

    @PutMapping("/rules/{id}")
    @Permit(PermissionCodes.JOB_MANAGE)
    public R<JobAlarm> updateRule(@PathVariable Long id, @RequestBody AlarmRuleRequest req) {
        return R.ok(alarmService.saveRule(id, req));
    }

    @DeleteMapping("/rules/{id}")
    @Permit(PermissionCodes.JOB_MANAGE)
    public R<Void> deleteRule(@PathVariable Long id) {
        alarmService.deleteRule(id);
        return R.ok();
    }

    @GetMapping("/records")
    @Permit(PermissionCodes.JOB_VIEW)
    public R<PageResult<AlarmRecordVO>> records(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        return R.ok(alarmService.listRecords(page, size));
    }
}

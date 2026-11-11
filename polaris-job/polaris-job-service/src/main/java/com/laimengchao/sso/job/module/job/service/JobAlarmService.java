package com.laimengchao.sso.job.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.job.module.job.dto.AlarmChannelRequest;
import com.laimengchao.sso.job.module.job.dto.AlarmRuleRequest;
import com.laimengchao.sso.job.module.job.entity.JobAlarm;
import com.laimengchao.sso.job.module.job.entity.JobAlarmChannel;
import com.laimengchao.sso.job.module.job.entity.JobAlarmLog;
import com.laimengchao.sso.job.module.job.entity.JobInfo;
import com.laimengchao.sso.job.module.job.entity.JobLog;
import com.laimengchao.sso.job.module.job.mapper.JobAlarmChannelMapper;
import com.laimengchao.sso.job.module.job.mapper.JobAlarmLogMapper;
import com.laimengchao.sso.job.module.job.mapper.JobAlarmMapper;
import com.laimengchao.sso.job.module.job.mapper.JobInfoMapper;
import com.laimengchao.sso.job.module.job.mapper.JobLogMapper;
import com.laimengchao.sso.job.module.job.vo.AlarmRecordVO;
import com.laimengchao.sso.settings.client.api.NoticeApi;
import com.laimengchao.sso.settings.client.dto.NoticeSendDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 失败报警：规则匹配 → 静默期去重 → 发送（站内消息/邮件）→ 写 {@code job_alarm_log}。
 * <p>
 * 通知统一走 settings 的 {@link NoticeApi}：站内消息复用消息中心，邮件由 settings 的 SMTP 发送。
 * 优先级：任务级 &gt; 应用级 &gt; 全局，命中高优先级规则后不再向下匹配。
 */
@Slf4j
@Service
public class JobAlarmService {

    private static final String EVENT_FAIL = "FAIL";
    private static final String EVENT_RECOVER = "RECOVER";
    private static final String CHANNEL_SITE = "SITE";
    private static final String CHANNEL_EMAIL = "EMAIL";

    private final JobAlarmMapper alarmMapper;
    private final JobAlarmLogMapper alarmLogMapper;
    private final JobAlarmChannelMapper channelMapper;
    private final JobInfoMapper jobInfoMapper;
    private final JobLogMapper jobLogMapper;
    private final NoticeApi noticeApi;

    public JobAlarmService(JobAlarmMapper alarmMapper,
                           JobAlarmLogMapper alarmLogMapper,
                           JobAlarmChannelMapper channelMapper,
                           JobInfoMapper jobInfoMapper,
                           JobLogMapper jobLogMapper,
                           NoticeApi noticeApi) {
        this.alarmMapper = alarmMapper;
        this.alarmLogMapper = alarmLogMapper;
        this.channelMapper = channelMapper;
        this.jobInfoMapper = jobInfoMapper;
        this.jobLogMapper = jobLogMapper;
        this.noticeApi = noticeApi;
    }

    // ---------------------------------------------------------------- 全局渠道

    /** 固定返回 SITE / EMAIL 两行，缺失时自动补一条停用记录 */
    public List<JobAlarmChannel> listChannels() {
        List<JobAlarmChannel> rows = new ArrayList<>(channelMapper.selectList(null));
        for (String channel : List.of(CHANNEL_SITE, CHANNEL_EMAIL)) {
            if (rows.stream().noneMatch(row -> channel.equals(row.getChannel()))) {
                JobAlarmChannel row = new JobAlarmChannel();
                row.setChannel(channel);
                row.setEnabled(0);
                channelMapper.insert(row);
                rows.add(row);
            }
        }
        rows.sort(Comparator.comparing(JobAlarmChannel::getChannel));
        return rows;
    }

    public void saveChannels(List<AlarmChannelRequest> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<JobAlarmChannel> rows = listChannels();
        for (AlarmChannelRequest item : items) {
            if (item == null || !StringUtils.hasText(item.getChannel())) {
                continue;
            }
            rows.stream()
                    .filter(row -> item.getChannel().trim().equalsIgnoreCase(row.getChannel()))
                    .findFirst()
                    .ifPresent(row -> {
                        row.setEnabled(item.getEnabled() != null && item.getEnabled() == 1 ? 1 : 0);
                        channelMapper.updateById(row);
                    });
        }
    }

    // ---------------------------------------------------------------- 规则

    public PageResult<JobAlarm> listRules(long pageNo, long size) {
        long page = Math.max(1, pageNo);
        long pageSize = Math.min(200, Math.max(1, size));
        Page<JobAlarm> result = alarmMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<JobAlarm>().orderByDesc(JobAlarm::getCreatedAt));
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    public JobAlarm saveRule(Long id, AlarmRuleRequest req) {
        if (req == null || !StringUtils.hasText(req.getName())) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        JobAlarm rule;
        if (id == null) {
            rule = new JobAlarm();
        } else {
            rule = alarmMapper.selectById(id);
            if (rule == null) {
                throw new IllegalArgumentException("报警规则不存在");
            }
        }
        rule.setName(req.getName().trim());
        rule.setScopeType(normalizeScope(req.getScopeType()));
        rule.setScopeValue(req.getScopeValue() == null ? "" : req.getScopeValue().trim());
        rule.setTriggerCondition(normalizeCondition(req.getTriggerCondition()));
        rule.setThreshold(req.getThreshold() == null ? 1 : Math.max(1, req.getThreshold()));
        rule.setChannels(normalizeChannels(req.getChannels()));
        rule.setAccountIds(req.getAccountIds() == null ? "" : req.getAccountIds().trim());
        rule.setSilenceMinutes(req.getSilenceMinutes() == null ? 60 : Math.max(0, req.getSilenceMinutes()));
        rule.setStatus(req.getStatus() != null && req.getStatus() == 0 ? 0 : 1);
        if (id == null) {
            alarmMapper.insert(rule);
        } else {
            alarmMapper.updateById(rule);
        }
        return rule;
    }

    public void deleteRule(Long id) {
        if (id != null) {
            alarmMapper.deleteById(id);
        }
    }

    // ---------------------------------------------------------------- 流水

    public PageResult<AlarmRecordVO> listRecords(long pageNo, long size) {
        long page = Math.max(1, pageNo);
        long pageSize = Math.min(200, Math.max(1, size));
        Page<JobAlarmLog> result = alarmLogMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<JobAlarmLog>().orderByDesc(JobAlarmLog::getCreatedAt));
        Map<Long, JobInfo> cache = new HashMap<>();
        List<AlarmRecordVO> records = result.getRecords().stream()
                .map(row -> toVO(row, cache))
                .toList();
        return PageResult.of(records, result.getTotal(), page, pageSize);
    }

    // ---------------------------------------------------------------- 调度回调

    /** 最终失败（重试次数已用尽）时调用 */
    public void onJobFailure(JobInfo job, Integer status, String message) {
        if (job == null || job.getId() == null) {
            return;
        }
        try {
            List<JobAlarm> rules = matchingRules(job);
            if (rules.isEmpty()) {
                return;
            }
            Set<String> enabled = enabledChannels();
            for (JobAlarm rule : rules) {
                if (!conditionMatched(rule, job, status)) {
                    continue;
                }
                if (inSilence(job.getId(), rule.getId(), rule.getSilenceMinutes())) {
                    log.debug("报警静默期内，跳过：job={} rule={}", job.getId(), rule.getId());
                    continue;
                }
                notify(job, rule, EVENT_FAIL, "任务失败：" + job.getName(),
                        "应用 " + job.getAppCode() + " 的任务「" + job.getName() + "」执行失败："
                                + reason(status, message),
                        rule.getChannels(), rule.getAccountIds(), enabled);
            }
        } catch (Exception e) {
            log.warn("报警触发失败：job={} reason={}", job.getId(), e.getMessage());
        }
    }

    /** 执行成功时调用：若上一次是失败报警，发一条「已恢复」 */
    public void onJobSuccess(JobInfo job) {
        if (job == null || job.getId() == null) {
            return;
        }
        try {
            JobAlarmLog last = latestRecord(job.getId());
            if (last == null || !EVENT_FAIL.equals(last.getEvent())) {
                return;
            }
            JobAlarm rule = last.getRuleId() == null ? null : alarmMapper.selectById(last.getRuleId());
            String channels = rule != null ? rule.getChannels() : last.getChannel();
            String accountIds = rule != null ? rule.getAccountIds() : last.getReceivers();
            notify(job, rule, EVENT_RECOVER, "任务已恢复：" + job.getName(),
                    "应用 " + job.getAppCode() + " 的任务「" + job.getName() + "」已恢复正常",
                    channels, accountIds, enabledChannels());
        } catch (Exception e) {
            log.warn("恢复通知发送失败：job={} reason={}", job.getId(), e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 内部实现

    private List<JobAlarm> matchingRules(JobInfo job) {
        List<JobAlarm> all = alarmMapper.selectList(new LambdaQueryWrapper<JobAlarm>()
                .eq(JobAlarm::getStatus, 1));
        String jobId = String.valueOf(job.getId());
        List<JobAlarm> jobRules = all.stream()
                .filter(rule -> "JOB".equalsIgnoreCase(rule.getScopeType()) && jobId.equals(rule.getScopeValue()))
                .toList();
        if (!jobRules.isEmpty()) {
            return jobRules;
        }
        List<JobAlarm> appRules = all.stream()
                .filter(rule -> "APP".equalsIgnoreCase(rule.getScopeType())
                        && job.getAppCode() != null && job.getAppCode().equalsIgnoreCase(rule.getScopeValue()))
                .toList();
        if (!appRules.isEmpty()) {
            return appRules;
        }
        return all.stream().filter(rule -> "ALL".equalsIgnoreCase(rule.getScopeType())).toList();
    }

    private boolean conditionMatched(JobAlarm rule, JobInfo job, Integer status) {
        String condition = StringUtils.hasText(rule.getTriggerCondition()) ? rule.getTriggerCondition() : "FAIL";
        return switch (condition) {
            case "TIMEOUT" -> Integer.valueOf(3).equals(status);
            case "CONSECUTIVE_FAIL" -> consecutiveFailures(job.getId(), rule.getThreshold()) >=
                    (rule.getThreshold() == null ? 1 : Math.max(1, rule.getThreshold()));
            default -> true;
        };
    }

    private int consecutiveFailures(Long jobId, Integer threshold) {
        int limit = threshold == null ? 1 : Math.max(1, threshold);
        List<JobLog> recent = jobLogMapper.selectList(new LambdaQueryWrapper<JobLog>()
                .eq(JobLog::getJobId, jobId)
                .orderByDesc(JobLog::getCreatedAt)
                .last("limit " + limit));
        return (int) recent.stream()
                .filter(row -> row.getStatus() != null && row.getStatus() != 1 && row.getStatus() != 0)
                .count();
    }

    private boolean inSilence(Long jobId, Long ruleId, Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return false;
        }
        List<JobAlarmLog> rows = alarmLogMapper.selectList(new LambdaQueryWrapper<JobAlarmLog>()
                .eq(JobAlarmLog::getJobId, jobId)
                .eq(JobAlarmLog::getEvent, EVENT_FAIL)
                .eq(ruleId != null, JobAlarmLog::getRuleId, ruleId)
                .ge(JobAlarmLog::getCreatedAt, LocalDateTime.now().minusMinutes(minutes))
                .last("limit 1"));
        return !rows.isEmpty();
    }

    private JobAlarmLog latestRecord(Long jobId) {
        List<JobAlarmLog> rows = alarmLogMapper.selectList(new LambdaQueryWrapper<JobAlarmLog>()
                .eq(JobAlarmLog::getJobId, jobId)
                .orderByDesc(JobAlarmLog::getCreatedAt)
                .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Set<String> enabledChannels() {
        Set<String> set = new HashSet<>();
        for (JobAlarmChannel row : listChannels()) {
            if (Integer.valueOf(1).equals(row.getEnabled())) {
                set.add(row.getChannel());
            }
        }
        return set;
    }

    private boolean notify(JobInfo job, JobAlarm rule, String event, String title, String content,
                           String channels, String accountIds, Set<String> enabled) {
        List<Long> accounts = parseIds(accountIds);
        boolean site = enabled.contains(CHANNEL_SITE) && hasChannel(channels, CHANNEL_SITE);
        boolean mail = enabled.contains(CHANNEL_EMAIL) && hasChannel(channels, CHANNEL_EMAIL);
        if ((!site && !mail) || accounts.isEmpty()) {
            log.debug("报警渠道未启用或无接收人，跳过：job={} rule={} channels={} accounts={}",
                    job.getId(), rule == null ? null : rule.getId(), channels, accountIds);
            return false;
        }

        int status = 2;
        try {
            NoticeSendDTO dto = new NoticeSendDTO();
            dto.setRuleCode("job.alarm");
            dto.setAccountIds(accounts);
            dto.setTitle(title);
            dto.setContent(content);
            dto.setBizType("job_alarm");
            dto.setBizId(String.valueOf(job.getId()));
            dto.setLink("/alarms");
            dto.setEmail(mail);
            com.laimengchao.sso.settings.client.dto.R<Integer> resp = noticeApi.sendBatch(dto);
            if (resp != null && resp.ok()) {
                status = 1;
            }
        } catch (Exception e) {
            log.warn("报警通知发送失败：job={} rule={} event={} reason={}",
                    job.getId(), rule == null ? null : rule.getId(), event, e.getMessage());
        }

        JobAlarmLog row = new JobAlarmLog();
        row.setJobId(job.getId());
        row.setRuleId(rule == null ? null : rule.getId());
        row.setEvent(event);
        row.setChannel(channelText(site, mail));
        row.setReceivers(accountIds);
        row.setContent(content);
        row.setStatus(status);
        row.setSentAt(LocalDateTime.now());
        alarmLogMapper.insert(row);
        return status == 1;
    }

    private AlarmRecordVO toVO(JobAlarmLog row, Map<Long, JobInfo> cache) {
        AlarmRecordVO vo = new AlarmRecordVO();
        vo.setId(row.getId());
        vo.setJobId(row.getJobId());
        vo.setEvent(row.getEvent());
        vo.setChannel(row.getChannel());
        vo.setReceivers(row.getReceivers());
        vo.setContent(row.getContent());
        vo.setStatus(row.getStatus());
        vo.setSentAt(row.getSentAt());
        vo.setCreatedAt(row.getCreatedAt());
        if (row.getJobId() != null) {
            JobInfo job = cache.computeIfAbsent(row.getJobId(), jobInfoMapper::selectById);
            if (job != null) {
                vo.setJobName(job.getName());
                vo.setAppCode(job.getAppCode());
            }
        }
        return vo;
    }

    private String reason(Integer status, String message) {
        String prefix = Integer.valueOf(3).equals(status) ? "执行超时/不可达" : "执行失败";
        return StringUtils.hasText(message) ? prefix + "：" + message : prefix;
    }

    private List<Long> parseIds(String text) {
        List<Long> ids = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return ids;
        }
        for (String part : text.split(",")) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // 非数字账号 ID 直接忽略
            }
        }
        return ids;
    }

    private boolean hasChannel(String channels, String channel) {
        if (!StringUtils.hasText(channels)) {
            return CHANNEL_SITE.equals(channel);
        }
        for (String part : channels.split(",")) {
            if (channel.equalsIgnoreCase(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private String channelText(boolean site, boolean mail) {
        if (site && mail) {
            return CHANNEL_SITE + "," + CHANNEL_EMAIL;
        }
        return site ? CHANNEL_SITE : CHANNEL_EMAIL;
    }

    private String normalizeScope(String scope) {
        String value = scope == null ? "ALL" : scope.trim().toUpperCase();
        return switch (value) {
            case "JOB", "APP" -> value;
            default -> "ALL";
        };
    }

    private String normalizeCondition(String condition) {
        String value = condition == null ? "FAIL" : condition.trim().toUpperCase();
        return switch (value) {
            case "TIMEOUT", "CONSECUTIVE_FAIL" -> value;
            default -> "FAIL";
        };
    }

    private String normalizeChannels(String channels) {
        List<String> values = new ArrayList<>();
        for (String part : (channels == null ? "" : channels).split(",")) {
            String value = part.trim().toUpperCase();
            if (CHANNEL_SITE.equals(value) && !values.contains(CHANNEL_SITE)) {
                values.add(CHANNEL_SITE);
            }
            if (CHANNEL_EMAIL.equals(value) && !values.contains(CHANNEL_EMAIL)) {
                values.add(CHANNEL_EMAIL);
            }
        }
        return values.isEmpty() ? CHANNEL_SITE : String.join(",", values);
    }
}

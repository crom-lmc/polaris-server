package com.laimengchao.sso.job.module.job.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.job.common.JobErrorCode;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CRON 校验：统一 6 位 Quartz 表达式（秒 分 时 日 月 周）。
 * <p>
 * 前端会先校验一次，这里必须再校验——不能把「绕过前端」当成不可能。
 * 日与周必须互斥（一个为 ?），否则含义歧义；同时试算未来 3 次，避免保存一个永远不会触发的死配置。
 */
@Service
public class CronService {

    private static final int PREVIEW_COUNT = 3;

    /** 规范化：trim + 多空白折叠为单空格，避免同义不同串 */
    public String normalize(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new BizException(JobErrorCode.JOB_CRON_INVALID, "CRON 表达式不能为空");
        }
        String normalized = cron.trim().replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ");
        if (parts.length != 6) {
            throw new BizException(JobErrorCode.JOB_CRON_INVALID,
                    "CRON 必须为 6 段（秒 分 时 日 月 周），当前为 " + parts.length + " 段");
        }
        boolean dayOfMonthIsQuestion = "?".equals(parts[3]);
        boolean dayOfWeekIsQuestion = "?".equals(parts[5]);
        if (dayOfMonthIsQuestion == dayOfWeekIsQuestion) {
            throw new BizException(JobErrorCode.JOB_CRON_INVALID,
                    "日与周必须互斥：其中必须且只能有一个为 ?");
        }
        return normalized;
    }

    /** 校验并返回未来 3 次执行时间 */
    public List<LocalDateTime> preview(String cron) {
        return preview(cron, LocalDateTime.now());
    }

    public List<LocalDateTime> preview(String cron, LocalDateTime base) {
        String normalized = normalize(cron);
        CronExpression expression;
        try {
            expression = CronExpression.parse(normalized);
        } catch (IllegalArgumentException e) {
            throw new BizException(JobErrorCode.JOB_CRON_INVALID,
                    "CRON 解析失败：" + e.getMessage());
        }
        List<LocalDateTime> times = new ArrayList<>(PREVIEW_COUNT);
        LocalDateTime cursor = base;
        for (int i = 0; i < PREVIEW_COUNT; i++) {
            LocalDateTime next = expression.next(cursor);
            if (next == null) {
                break;
            }
            times.add(next);
            cursor = next;
        }
        if (times.size() < PREVIEW_COUNT) {
            throw new BizException(JobErrorCode.JOB_CRON_INVALID,
                    "CRON 在未来无法连续触发 " + PREVIEW_COUNT + " 次，请检查配置");
        }
        return times;
    }

    /** 保存时取第一次触发时间 */
    public LocalDateTime nextFireTime(String cron, LocalDateTime base) {
        return preview(cron, base).get(0);
    }
}

package com.laimengchao.sso.job.module.job.service;

import com.laimengchao.sso.common.BizException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronServiceTest {

    private final CronService cronService = new CronService();

    @Test
    void acceptsSixFieldQuartzCron() {
        assertThat(cronService.normalize("0 0 2 ? * *")).isEqualTo("0 0 2 ? * *");
        assertThat(cronService.normalize("0 30 1 ? * 1-5")).isEqualTo("0 30 1 ? * 1-5");
    }

    @Test
    void foldsWhitespace() {
        assertThat(cronService.normalize("  0   0  2  ?  *  * ")).isEqualTo("0 0 2 ? * *");
    }

    @Test
    void rejectsWhenDayAndWeekAreNotMutuallyExclusive() {
        assertThatThrownBy(() -> cronService.normalize("0 0 2 * * *"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("日与周必须互斥");
        assertThatThrownBy(() -> cronService.normalize("0 0 2 ? * ?"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("日与周必须互斥");
    }

    @Test
    void rejectsSevenFieldsWithYear() {
        assertThatThrownBy(() -> cronService.normalize("0 0 2 ? * * 2027"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("6 段");
    }

    @Test
    void rejectsInvalidExpression() {
        assertThatThrownBy(() -> cronService.preview("0 0 99 ? * *"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("CRON 解析失败");
    }

    @Test
    void previewReturnsThreeFutureTimes() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        List<LocalDateTime> times = cronService.preview("0 0 2 ? * *", base);
        assertThat(times).containsExactly(
                LocalDateTime.of(2026, 1, 1, 2, 0, 0),
                LocalDateTime.of(2026, 1, 2, 2, 0, 0),
                LocalDateTime.of(2026, 1, 3, 2, 0, 0));
    }

    @Test
    void rejectsDeadCron() {
        // 2 月 30 日永远不会到来，试算不足 3 次
        assertThatThrownBy(() -> cronService.preview("0 0 0 30 2 ?"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无法连续触发");
    }
}

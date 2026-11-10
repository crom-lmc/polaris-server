package com.laimengchao.sso.ehr.module.attendance.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 法定节假日与调休上班日预设（2026 年依据国办发明电〔2025〕7号；2024–2025 年依据国务院办公厅
 * 历年《部分节假日安排的通知》，2025 年起全体公民放假的节日增加除夕与 5 月 2 日两天）。
 * 仅作为数据库未显式配置时的兜底：页面保存或 CSV 导入后，以数据库中的工作日历为准。
 */
final class AttendanceHolidayPreset {

    private static final Map<LocalDate, String> HOLIDAYS = new HashMap<>();
    private static final Map<LocalDate, String> MAKEUP_WORKDAYS = new HashMap<>();

    static {
        // ==================== 2024 年 ====================
        // 元旦：1月1日放假，与周末连休
        addHoliday(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 1), "元旦");
        // 春节：2月10日至17日放假，2月4日、2月18日上班
        addHoliday(LocalDate.of(2024, 2, 10), LocalDate.of(2024, 2, 17), "春节");
        addMakeup(LocalDate.of(2024, 2, 4));
        addMakeup(LocalDate.of(2024, 2, 18));
        // 清明节：4月4日至6日放假，4月7日上班
        addHoliday(LocalDate.of(2024, 4, 4), LocalDate.of(2024, 4, 6), "清明节");
        addMakeup(LocalDate.of(2024, 4, 7));
        // 劳动节：5月1日至5日放假，4月28日、5月11日上班
        addHoliday(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 5), "劳动节");
        addMakeup(LocalDate.of(2024, 4, 28));
        addMakeup(LocalDate.of(2024, 5, 11));
        // 端午节：6月10日放假，与周末连休
        addHoliday(LocalDate.of(2024, 6, 8), LocalDate.of(2024, 6, 10), "端午节");
        // 中秋节：9月15日至17日放假，9月14日上班
        addHoliday(LocalDate.of(2024, 9, 15), LocalDate.of(2024, 9, 17), "中秋节");
        addMakeup(LocalDate.of(2024, 9, 14));
        // 国庆节：10月1日至7日放假，9月29日、10月12日上班
        addHoliday(LocalDate.of(2024, 10, 1), LocalDate.of(2024, 10, 7), "国庆节");
        addMakeup(LocalDate.of(2024, 9, 29));
        addMakeup(LocalDate.of(2024, 10, 12));

        // ==================== 2025 年 ====================
        // 元旦：1月1日放假1天，不调休
        addHoliday(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1), "元旦");
        // 春节：1月28日（除夕）至2月4日放假，1月26日、2月8日上班
        addHoliday(LocalDate.of(2025, 1, 28), LocalDate.of(2025, 2, 4), "春节");
        addMakeup(LocalDate.of(2025, 1, 26));
        addMakeup(LocalDate.of(2025, 2, 8));
        // 清明节：4月4日至6日放假
        addHoliday(LocalDate.of(2025, 4, 4), LocalDate.of(2025, 4, 6), "清明节");
        // 劳动节：5月1日至5日放假，4月27日上班
        addHoliday(LocalDate.of(2025, 5, 1), LocalDate.of(2025, 5, 5), "劳动节");
        addMakeup(LocalDate.of(2025, 4, 27));
        // 端午节：5月31日至6月2日放假
        addHoliday(LocalDate.of(2025, 5, 31), LocalDate.of(2025, 6, 2), "端午节");
        // 国庆节、中秋节：10月1日至8日放假，9月28日、10月11日上班
        addHoliday(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 8), "国庆节");
        addMakeup(LocalDate.of(2025, 9, 28));
        addMakeup(LocalDate.of(2025, 10, 11));

        // ==================== 2026 年 ====================
        // 元旦：1月1日至3日放假，1月4日上班
        addHoliday(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), "元旦");
        addMakeup(LocalDate.of(2026, 1, 4));
        // 春节：2月15日至23日放假，2月14日、2月28日上班
        addHoliday(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23), "春节");
        addMakeup(LocalDate.of(2026, 2, 14));
        addMakeup(LocalDate.of(2026, 2, 28));
        // 清明节：4月4日至6日放假
        addHoliday(LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6), "清明节");
        // 劳动节：5月1日至5日放假，5月9日上班
        addHoliday(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), "劳动节");
        addMakeup(LocalDate.of(2026, 5, 9));
        // 端午节：6月19日至21日放假
        addHoliday(LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21), "端午节");
        // 中秋节：9月25日至27日放假
        addHoliday(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27), "中秋节");
        // 国庆节：10月1日至7日放假，9月20日、10月10日上班
        addHoliday(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), "国庆节");
        addMakeup(LocalDate.of(2026, 9, 20));
        addMakeup(LocalDate.of(2026, 10, 10));

        // ==================== 2027 年 ====================
        // 国务院正式安排通常在上一年的 10–11 月发布；以下为按《全国年节及纪念日放假办法》
        // 与农历推算的预估，HR 可在工作日历页面或 CSV 导入随时调整，库内显式配置优先。
        // 元旦：1月1日至3日放假
        addHoliday(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 3), "元旦");
        // 春节：2月5日（除夕）至2月11日放假，2月13日上班
        addHoliday(LocalDate.of(2027, 2, 5), LocalDate.of(2027, 2, 11), "春节");
        addMakeup(LocalDate.of(2027, 2, 13));
        // 清明节：4月3日至5日放假
        addHoliday(LocalDate.of(2027, 4, 3), LocalDate.of(2027, 4, 5), "清明节");
        // 劳动节：5月1日至5日放假，5月8日上班
        addHoliday(LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 5), "劳动节");
        addMakeup(LocalDate.of(2027, 5, 8));
        // 端午节：6月9日放假
        addHoliday(LocalDate.of(2027, 6, 9), LocalDate.of(2027, 6, 9), "端午节");
        // 中秋节：9月15日放假
        addHoliday(LocalDate.of(2027, 9, 15), LocalDate.of(2027, 9, 15), "中秋节");
        // 国庆节：10月1日至7日放假，9月26日、10月9日上班
        addHoliday(LocalDate.of(2027, 10, 1), LocalDate.of(2027, 10, 7), "国庆节");
        addMakeup(LocalDate.of(2027, 9, 26));
        addMakeup(LocalDate.of(2027, 10, 9));
    }

    private AttendanceHolidayPreset() {
    }

    /** 预设法定节假日名称，非节假日返回 null。 */
    static String holidayName(LocalDate date) {
        return HOLIDAYS.get(date);
    }

    /** 预设调休上班日：周末因调休需正常上班。 */
    static boolean isMakeupWorkday(LocalDate date) {
        return MAKEUP_WORKDAYS.containsKey(date);
    }

    /** 预设调休上班标记名称，非调休日返回 null。 */
    static String makeupName(LocalDate date) {
        return MAKEUP_WORKDAYS.get(date);
    }

    /** 指定年份的全部预设法定节假日（日期 → 节日名），用于「一键更新下一年工作日历」。 */
    static Map<LocalDate, String> holidaysOf(int year) {
        Map<LocalDate, String> out = new HashMap<>();
        HOLIDAYS.forEach((d, name) -> {
            if (d.getYear() == year) {
                out.put(d, name);
            }
        });
        return out;
    }

    /** 指定年份的全部预设调休上班日。 */
    static Map<LocalDate, String> makeupsOf(int year) {
        Map<LocalDate, String> out = new HashMap<>();
        MAKEUP_WORKDAYS.forEach((d, name) -> {
            if (d.getYear() == year) {
                out.put(d, name);
            }
        });
        return out;
    }

    private static void addHoliday(LocalDate from, LocalDate to, String name) {
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            HOLIDAYS.put(d, name);
        }
    }

    private static void addMakeup(LocalDate date) {
        MAKEUP_WORKDAYS.put(date, "调休上班");
    }
}

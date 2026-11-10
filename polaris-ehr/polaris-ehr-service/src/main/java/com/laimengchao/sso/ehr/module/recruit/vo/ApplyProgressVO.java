package com.laimengchao.sso.ehr.module.recruit.vo;

import lombok.Data;

import java.util.List;

/** 门户投递结果 / 进度查询出参 */
@Data
public class ApplyProgressVO {

    private String trackCode;
    private String candidateName;
    private String jobTitle;
    private String stage;
    private String stageLabel;
    private String appliedAt;
    private String lastStageAt;
    private List<TimelineItem> timeline;
    /** 各轮面试进度（不展示面试官） */
    private List<RoundItem> rounds;

    @Data
    public static class TimelineItem {
        private String stage;
        private String label;
        private String time;
        private Boolean done;
    }

    @Data
    public static class RoundItem {
        private Integer roundNo;
        private String label;
        /** 约定面试时间，未安排为空 */
        private String time;
        /** ONSITE/VIDEO/PHONE */
        private String method;
        private String methodLabel;
        /** 面试地址或会议链接 */
        private String location;
        /** PENDING 待面试 / PASS 已通过 / FAIL 未通过 */
        private String status;
        private String statusLabel;
    }
}

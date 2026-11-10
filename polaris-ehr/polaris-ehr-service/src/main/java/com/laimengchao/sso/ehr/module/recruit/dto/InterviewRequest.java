package com.laimengchao.sso.ehr.module.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** HR 约面试：一轮可约多位面试官，轮次不固定；候选人来自路径参数 */
@Data
public class InterviewRequest {

    @NotEmpty(message = "请选择面试官")
    private List<Long> interviewerIds;

    /** 约定时间：yyyy-MM-dd HH:mm(:ss) */
    @NotBlank(message = "请选择面试时间")
    private String scheduledAt;

    /** ONSITE / VIDEO / PHONE */
    private String method;

    /** 面试地点或会议链接 */
    private String location;

    private String remark;
}

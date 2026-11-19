package com.laimengchao.sso.forum.module.report.dto;

import lombok.Data;

@Data
public class ReportCreateRequest {

    private Object targetType;
    private Object targetId;
    private Object reason;
    private Object detail;
}

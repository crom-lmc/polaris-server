package com.laimengchao.sso.forum.module.report.dto;

import com.laimengchao.sso.forum.module.report.entity.Report;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReportDetailDTO extends Report {

    private String reporterName;
    private String threadTitle;
    private String targetContent;
}

package com.laimengchao.sso.ehr.module.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 通知新建 / 编辑入参（富文本正文按 HTML 原样落库） */
@Data
public class AnnouncementSaveRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @Size(max = 500, message = "摘要不能超过 500 字")
    private String summary;

    /** 取值见配置中心字典 ehr.announcement_category（HOLIDAY/COMMENDATION/DEPARTMENT/POLICY/APPOINTMENT/OTHER），留空按 OTHER */
    private String category;

    @NotBlank(message = "通知内容不能为空")
    private String content;

    /** 0 全员（集团） / 1 按部门 */
    @NotNull(message = "请选择通知目标")
    private Integer targetType;

    /** 按部门时必填；全员时忽略 */
    private List<Long> targetOrgIds;

    /** true 置顶 */
    private Boolean pinned;

    @Size(max = 255, message = "备注不能超过 255 字")
    private String remark;
}

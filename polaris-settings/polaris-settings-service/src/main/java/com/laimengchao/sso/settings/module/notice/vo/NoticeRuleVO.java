package com.laimengchao.sso.settings.module.notice.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 消息配置列表项 */
@Data
public class NoticeRuleVO {
    /** 雪花ID序列化成字符串，前端编辑/删除回传时才不会丢精度 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String code;
    /** 归属应用编码，空=通用 */
    private String appCode;
    private String module;
    private String name;
    private String titleTpl;
    private String contentTpl;
    /** 1 待办 / 2 消息 */
    private Integer channel;
    private Integer email;
    private Integer enabled;
    private Integer builtIn;
    private Integer sort;
    private LocalDateTime createdAt;
}

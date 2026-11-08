package com.laimengchao.sso.ehr.module.org.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 负责人变更记录视图：给组织详情页的时间线用。
 * <p>
 * 所有雪花 ID 一律序列化成字符串（19 位超过 JS 安全整数，直接给数字会丢精度）；
 * {@code approvalInstanceId} 用 {@code ALWAYS} 输出——前端靠它判断「能不能点开审批单」，
 * 字段被 non_null 省略会让前端读不到 null 与不存在的区别。
 */
@Data
public class LeaderChangeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;
    private String orgName;
    private String orgPath;
    private String orgType;

    /** SET / CHANGE / CLEAR */
    private String changeType;
    /** 中文语义：首次设置 / 更换负责人 / 清除负责人 */
    private String changeTypeName;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long oldLeaderId;
    private String oldLeaderName;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long newLeaderId;
    private String newLeaderName;
    private String newLeaderEmpNo;
    private String newLeaderPosition;

    /** 变更原因 */
    private String reason;

    /** 0 审批中 / 1 已生效 / 2 已驳回 / 3 已撤销 */
    private Integer status;
    private String statusName;

    private String applicantName;

    /** 在途/历史审批单 ID：前端据此打开审批详情；无审批单时为 null 但仍输出字段 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;

    /** 提交时间（= createdAt，单独给一个语义化名字） */
    private LocalDateTime appliedAt;
    /** 生效时间 */
    private LocalDateTime effectiveAt;
    private String remark;
}

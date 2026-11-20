package com.laimengchao.sso.forum.module.forum.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ForumExchange {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long giftId;
    private String username;
    private String giftName;
    private Integer points;
    /** 来源：EXCHANGE 积分兑换、LOTTERY 抽奖中奖 */
    private String source;
    private String status;
    private String trackingNo;
    private String createdAt;
    private String issuedByName;
    private String issuedAt;
    private String issueRemark;
}

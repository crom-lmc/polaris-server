package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录审计日志。不做逻辑删除，全部保留；查询按时间倒序。
 */
@Data
@TableName("sso_login_log")
public class SsoLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 成功时填账户ID，用户不存在等失败场景为 NULL */
    private Long accountId;
    /** 尝试登录的用户名（即使不存在也记录，便于排查撞库） */
    private String username;
    /** password / email / wechat */
    private String loginType;
    /** success / fail */
    private String result;
    /** 失败原因 */
    private String failReason;
    private String clientIp;
    private String userAgent;
    private LocalDateTime createdAt;
}

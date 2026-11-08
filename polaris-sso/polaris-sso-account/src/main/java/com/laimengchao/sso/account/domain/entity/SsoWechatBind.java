package com.laimengchao.sso.account.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 微信绑定关系。一个账户可绑定多个 openid（按 app_type 区分开放平台扫码 / 小程序）。
 */
@Data
@TableName("sso_wechat_bind")
public class SsoWechatBind {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;
    /** 微信 openid（同一 app_type 唯一） */
    private String openid;
    /** 开放平台 unionid */
    private String unionid;
    /** open=开放平台扫码 mp=小程序 */
    private String appType;
    /** 微信昵称快照 */
    private String nickname;
    /** 微信头像快照 */
    private String avatarUrl;
    private LocalDateTime bindAt;
}

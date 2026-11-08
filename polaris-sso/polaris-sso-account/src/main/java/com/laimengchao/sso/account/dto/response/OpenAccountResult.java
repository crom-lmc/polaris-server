package com.laimengchao.sso.account.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 开户结果。{@code initialPassword} 只在本次新建账号时有值，
 * 复用时为 null——账户中心不提供「查初始密码」的能力。
 */
@Data
public class OpenAccountResult {

    private Long accountId;
    private String username;
    private String initialPassword;
    /** true=本次新建；false=命中已有账号（按登录名或邮箱）并补齐授权 */
    private boolean created;
    /** 实际已授权的应用编码 */
    private List<String> apps = new ArrayList<>();
    /** 实际已授予的角色编码 */
    private List<String> roles = new ArrayList<>();
    /** 复用账号时给出提示，便于调用方展示 */
    private String notice;
}

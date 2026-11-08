package com.laimengchao.sso.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 中心会话快照：凭 cookie 就能拿到的「我是谁 + 我能进哪些应用」。
 * <p>
 * 前端在登录页/单点跳转前先调它：拿到就说明已有登录态，可以直接换票据跳回应用，
 * 不必再让用户输一次密码。
 */
@Data
public class SessionInfo {

    private Long accountId;
    private String username;
    private String nickname;
    private boolean superAdmin;
    private List<String> permissions;
    /** 可访问的应用编码 */
    private List<String> apps;
    /** 可访问应用的详情（含名称与地址，前端直接渲染） */
    private List<AppBrief> appList;
}

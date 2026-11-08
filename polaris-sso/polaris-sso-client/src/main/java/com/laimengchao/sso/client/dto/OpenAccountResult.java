package com.laimengchao.sso.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 开户结果。{@code initialPassword} 只在本次新建账号时有值，复用时为 null。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAccountResult {

    private Long accountId;
    private String username;
    /** 仅在本次新建账号时返回明文 */
    private String initialPassword;
    /** true=本次新建；false=命中已有账号并补齐授权 */
    private boolean created;
    /** 实际已授权的应用编码 */
    private List<String> apps;
    /** 实际已授予的角色编码 */
    private List<String> roles;
    /** 复用账号时给出提示 */
    private String notice;
}

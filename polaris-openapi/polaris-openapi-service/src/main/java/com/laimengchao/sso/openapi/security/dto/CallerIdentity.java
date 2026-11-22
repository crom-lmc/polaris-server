package com.laimengchao.sso.openapi.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 一次第三方调用的鉴权结论：应用编码 + 其被授予的开放 API 权限码。 */
@Data
@AllArgsConstructor
public class CallerIdentity {

    private String appCode;
    /** 权限码集合（inbound:/outbound: 前缀） */
    private java.util.Set<String> permissions;
}

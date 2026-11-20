package com.laimengchao.sso.ehr.integration;

import lombok.Data;

/**
 * 开户联动论坛成员的请求体（雪花 ID 按字符串下发，避免精度丢失）。
 */
@Data
public class ForumMemberRequest {

    private Object accountId;
    private String username;
}

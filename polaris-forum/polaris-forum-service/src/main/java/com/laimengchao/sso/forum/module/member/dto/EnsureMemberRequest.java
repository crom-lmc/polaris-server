package com.laimengchao.sso.forum.module.member.dto;

import lombok.Data;

/**
 * 开户联动登记论坛成员的内部请求（仅服务间调用，凭 X-Internal-Token 准入）。
 */
@Data
public class EnsureMemberRequest {

    /** 统一账号中心账号 ID（雪花 ID，按字符串传避免前端精度丢失） */
    private Object accountId;
    private String username;
}

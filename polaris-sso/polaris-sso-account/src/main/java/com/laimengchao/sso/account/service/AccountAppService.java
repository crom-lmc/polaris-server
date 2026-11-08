package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.dto.response.ApplicationVO;

import java.util.List;

/**
 * 账号-应用授权服务。
 * <p>
 * 超级管理员不查本表：登录时直接拿全部启用应用（见 AccountServiceImpl#buildLoginResult）。
 */
public interface AccountAppService {

    /** 账号被授权的应用 ID 列表（含已停用的，便于管理端回显勾选状态） */
    List<Long> appIds(Long accountId);

    /** 账号可用的应用详情（仅启用） */
    List<ApplicationVO> appsOf(Long accountId);

    /** 覆盖式授权：先清空再写入 */
    void grant(Long accountId, List<Long> appIds);

    /** 账号被授权且启用的应用编码——写进 JWT 的 apps claim */
    List<String> enabledCodes(Long accountId);
}

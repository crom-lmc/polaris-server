package com.laimengchao.sso.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.account.domain.entity.SsoAccountApp;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SsoAccountAppMapper extends BaseMapper<SsoAccountApp> {

    @Select("SELECT app_id FROM sso_account_app WHERE account_id = #{accountId}")
    List<Long> selectAppIdsByAccount(@Param("accountId") Long accountId);

    /** 账号被授权且处于启用状态的应用编码——写进 JWT 的 apps claim */
    @Select("""
            SELECT a.code FROM sso_account_app aa
            JOIN sso_application a ON aa.app_id = a.id
            WHERE aa.account_id = #{accountId} AND a.status = 1 AND a.deleted = 0
            ORDER BY a.sort, a.id
            """)
    List<String> selectEnabledCodesByAccount(@Param("accountId") Long accountId);

    @Select("SELECT account_id FROM sso_account_app WHERE app_id = #{appId}")
    List<Long> selectAccountIdsByApp(@Param("appId") Long appId);
}

package com.laimengchao.sso.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.account.domain.entity.SsoWechatBind;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SsoWechatBindMapper extends BaseMapper<SsoWechatBind> {

    @Select("SELECT * FROM sso_wechat_bind WHERE openid = #{openid} AND app_type = #{appType} LIMIT 1")
    SsoWechatBind selectByOpenid(@Param("openid") String openid, @Param("appType") String appType);

    @Select("SELECT * FROM sso_wechat_bind WHERE account_id = #{accountId} ORDER BY id DESC")
    java.util.List<SsoWechatBind> selectByAccount(@Param("accountId") Long accountId);
}

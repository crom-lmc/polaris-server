package com.laimengchao.sso.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.account.domain.entity.SsoEmailCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SsoEmailCodeMapper extends BaseMapper<SsoEmailCode> {

    /** 取该邮箱+场景下最新的一条未使用、未过期的验证码。 */
    @Select("""
            SELECT * FROM sso_email_code
            WHERE email = #{email} AND scene = #{scene} AND used = 0 AND expires_at > NOW()
            ORDER BY id DESC LIMIT 1
            """)
    SsoEmailCode latestValid(@Param("email") String email, @Param("scene") String scene);
}

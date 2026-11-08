package com.laimengchao.sso.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.account.domain.entity.SsoLoginLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SsoLoginLogMapper extends BaseMapper<SsoLoginLog> {

    /** 分页查询（时间倒序），按用户名与登录类型过滤。 */
    @Select("""
            SELECT * FROM sso_login_log
            WHERE (#{username} IS NULL OR username LIKE CONCAT('%', #{username}, '%'))
              AND (#{loginType} IS NULL OR login_type = #{loginType})
            ORDER BY id DESC
            LIMIT #{offset}, #{size}
            """)
    java.util.List<SsoLoginLog> page(@Param("username") String username,
                                     @Param("loginType") String loginType,
                                     @Param("offset") long offset,
                                     @Param("size") long size);

    @Select("""
            SELECT COUNT(1) FROM sso_login_log
            WHERE (#{username} IS NULL OR username LIKE CONCAT('%', #{username}, '%'))
              AND (#{loginType} IS NULL OR login_type = #{loginType})
            """)
    long count(@Param("username") String username, @Param("loginType") String loginType);
}

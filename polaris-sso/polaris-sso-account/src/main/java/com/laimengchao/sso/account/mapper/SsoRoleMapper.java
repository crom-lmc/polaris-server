package com.laimengchao.sso.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.account.domain.entity.SsoRole;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SsoRoleMapper extends BaseMapper<SsoRole> {

    @Select("SELECT * FROM sso_role WHERE code = #{code} AND deleted = 0")
    SsoRole selectByCode(@Param("code") String code);

    /** 含已逻辑删除的行：uk_role_code 不认 deleted，重建同码角色前必须先查到这里 */
    @Select("SELECT * FROM sso_role WHERE code = #{code} LIMIT 1")
    SsoRole selectByCodeIncludingDeleted(@Param("code") String code);

    /** 复活被逻辑删除的角色（@TableLogic 会拦 updateById，只能走原生 SQL） */
    @Update("UPDATE sso_role SET deleted = 0 WHERE id = #{id}")
    int restore(@Param("id") Long id);
}

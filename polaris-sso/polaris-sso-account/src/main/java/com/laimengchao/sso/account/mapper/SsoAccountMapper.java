package com.laimengchao.sso.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.account.domain.entity.SsoAccount;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SsoAccountMapper extends BaseMapper<SsoAccount> {

    @Select("SELECT * FROM sso_account WHERE username = #{username} AND deleted = 0")
    SsoAccount selectByUsername(@Param("username") String username);

    @Select("SELECT * FROM sso_account WHERE email = #{email} AND deleted = 0")
    SsoAccount selectByEmail(@Param("email") String email);

    @Select("SELECT COUNT(1) FROM sso_account WHERE username = #{username} AND deleted = 0 AND id <> #{id}")
    int countUsernameExceptId(@Param("username") String username, @Param("id") Long id);

    @Select("SELECT COUNT(1) FROM sso_account WHERE email = #{email} AND deleted = 0 AND id <> #{id}")
    int countEmailExceptId(@Param("email") String email, @Param("id") Long id);

    /**
     * 取账户在指定应用内的权限码（账户 -> 角色 -> 权限）。
     *
     * <p>必须带 appCode：权限码按应用分域，不同应用可以有同名码。返回的集合只包含
     * 该应用下的按钮/菜单码，业务应用拿到的 token 因此不会夹带平台自身的权限。
     */
    @Select("""
            SELECT DISTINCT p.code FROM sso_account_role ar
            JOIN sso_role_permission rp ON ar.role_id = rp.role_id
            JOIN sso_permission p ON rp.permission_id = p.id
            WHERE ar.account_id = #{accountId} AND p.deleted = 0 AND p.app_code = #{appCode}
            """)
    List<String> selectPermissionCodesByAccountId(@Param("accountId") Long accountId,
                                                  @Param("appCode") String appCode);

    @Select("""
            SELECT DISTINCT r.code FROM sso_account_role ar
            JOIN sso_role r ON ar.role_id = r.id
            WHERE ar.account_id = #{accountId} AND r.deleted = 0
            """)
    List<String> selectRoleCodesByAccountId(@Param("accountId") Long accountId);

    /** 某应用的全部权限码（超管视作拥有该应用全部权限） */
    @Select("SELECT code FROM sso_permission WHERE deleted = 0 AND app_code = #{appCode}")
    List<String> selectAllPermissionCodes(@Param("appCode") String appCode);
}

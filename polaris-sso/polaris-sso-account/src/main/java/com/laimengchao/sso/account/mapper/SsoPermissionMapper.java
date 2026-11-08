package com.laimengchao.sso.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.account.domain.entity.SsoPermission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SsoPermissionMapper extends BaseMapper<SsoPermission> {

    @Select("SELECT * FROM sso_permission WHERE app_code = #{appCode} AND code = #{code} AND deleted = 0")
    SsoPermission selectByCode(@Param("appCode") String appCode, @Param("code") String code);

    /**
     * 含已逻辑删除的行。唯一索引 uk_perm_app_code 不认 deleted 标记，
     * 所以「删掉再重建同码」必须先查到这里，否则会撞唯一键而不是给出清晰提示。
     */
    @Select("SELECT * FROM sso_permission WHERE app_code = #{appCode} AND code = #{code} LIMIT 1")
    SsoPermission selectByCodeIncludingDeleted(@Param("appCode") String appCode, @Param("code") String code);

    /** 复活被逻辑删除的行（@TableLogic 会拦 updateById，只能走原生 SQL） */
    @Update("UPDATE sso_permission SET deleted = 0 WHERE id = #{id}")
    int restore(@Param("id") Long id);

    /** 某应用下的全部权限（含菜单与按钮），按排序输出 */
    @Select("SELECT * FROM sso_permission WHERE app_code = #{appCode} AND deleted = 0 ORDER BY type DESC, sort, id")
    List<SsoPermission> selectByApp(@Param("appCode") String appCode);

    /** 统计某应用已注册的权限点数量（接入时核对清单用） */
    @Select("SELECT COUNT(1) FROM sso_permission WHERE app_code = #{appCode} AND deleted = 0")
    int countByApp(@Param("appCode") String appCode);
}

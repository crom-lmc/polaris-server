package com.laimengchao.sso.account.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.account.domain.entity.SsoApplication;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SsoApplicationMapper extends BaseMapper<SsoApplication> {

    @Select("SELECT * FROM sso_application WHERE code = #{code} AND deleted = 0")
    SsoApplication selectByCode(@Param("code") String code);

    /** 全部启用应用的编码（超管视作拥有全部应用） */
    @Select("SELECT code FROM sso_application WHERE status = 1 AND deleted = 0 ORDER BY sort, id")
    List<String> selectEnabledCodes();

    @Select("SELECT COUNT(1) FROM sso_application WHERE code = #{code} AND deleted = 0 AND id <> #{id}")
    int countCodeExceptId(@Param("code") String code, @Param("id") Long id);
}

package com.laimengchao.sso.openapi.module.setting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.openapi.module.setting.entity.ApiOpenSetting;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 平台设置 mapper。 */
public interface ApiOpenSettingMapper extends BaseMapper<ApiOpenSetting> {

    @Select("SELECT * FROM api_open_setting WHERE setting_key = #{key} AND deleted = 0")
    ApiOpenSetting selectByKey(@Param("key") String key);
}

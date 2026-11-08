package com.laimengchao.sso.blog.module.setting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.blog.module.setting.entity.Setting;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站点设置 Mapper。
 */
@Mapper
public interface SettingMapper extends BaseMapper<Setting> {
}

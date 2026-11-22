package com.laimengchao.sso.openapi.module.channel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.openapi.module.channel.entity.ApiOutboundChannel;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 出站渠道 mapper。 */
public interface ApiOutboundChannelMapper extends BaseMapper<ApiOutboundChannel> {

    @Select("SELECT * FROM api_outbound_channel WHERE channel_code = #{code} AND deleted = 0")
    ApiOutboundChannel selectByCode(@Param("code") String code);
}

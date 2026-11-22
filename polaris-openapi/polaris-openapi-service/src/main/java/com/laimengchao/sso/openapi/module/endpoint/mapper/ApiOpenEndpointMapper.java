package com.laimengchao.sso.openapi.module.endpoint.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 开放 API 目录 mapper。 */
public interface ApiOpenEndpointMapper extends BaseMapper<ApiOpenEndpoint> {

    @Select("SELECT * FROM api_open_endpoint WHERE api_code = #{apiCode} AND deleted = 0")
    ApiOpenEndpoint selectByCode(@Param("apiCode") String apiCode);
}

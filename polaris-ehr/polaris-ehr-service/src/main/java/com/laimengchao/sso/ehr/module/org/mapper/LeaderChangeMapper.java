package com.laimengchao.sso.ehr.module.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.org.entity.LeaderChange;
import org.apache.ibatis.annotations.Mapper;

/**
 * 负责人变更记录。查询一律走 MyBatis-Plus 条件构造器（自动带 deleted=0），
 * 不需要自定义 SQL：按组织查时间线、按实例号查在途单都只是一次索引扫描。
 */
@Mapper
public interface LeaderChangeMapper extends BaseMapper<LeaderChange> {
}

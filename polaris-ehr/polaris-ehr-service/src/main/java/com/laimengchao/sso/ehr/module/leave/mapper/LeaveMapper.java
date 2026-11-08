package com.laimengchao.sso.ehr.module.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeaveMapper extends BaseMapper<LeaveRequest> {
}

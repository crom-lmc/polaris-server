package com.laimengchao.sso.ehr.module.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveCarryoverWaiver;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface LeaveCarryoverWaiverMapper extends BaseMapper<LeaveCarryoverWaiver> {

    /** 某额度已签署放弃的结转天数合计 */
    @Select("SELECT COALESCE(SUM(waive_days), 0) FROM ehr_leave_carryover_waiver WHERE quota_id = #{quotaId}")
    BigDecimal sumWaiveDays(@Param("quotaId") Long quotaId);
}

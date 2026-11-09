package com.laimengchao.sso.ehr.module.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 额度变更全部使用「带余额条件的原子 UPDATE」：
 * 影响行数为 0 即余额不足 / 冻结额不够，由调用方抛业务异常，避免并发超额。
 */
@Mapper
public interface LeaveQuotaMapper extends BaseMapper<LeaveQuota> {

    @Update("UPDATE ehr_leave_quota SET frozen_days = frozen_days + #{days}, version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND total_days - used_days - frozen_days >= #{days}")
    int freeze(@Param("id") Long id, @Param("days") BigDecimal days);

    @Update("UPDATE ehr_leave_quota SET frozen_days = frozen_days - #{days}, used_days = used_days + #{days}, "
            + "version = version + 1 WHERE id = #{id} AND deleted = 0 AND frozen_days >= #{days}")
    int consume(@Param("id") Long id, @Param("days") BigDecimal days);

    @Update("UPDATE ehr_leave_quota SET frozen_days = frozen_days - #{days}, version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND frozen_days >= #{days}")
    int release(@Param("id") Long id, @Param("days") BigDecimal days);

    /** delta 可为负：调整后总额度不得低于已用 + 冻结 */
    @Update("UPDATE ehr_leave_quota SET total_days = total_days + #{delta}, version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND total_days + #{delta} >= used_days + frozen_days")
    int adjust(@Param("id") Long id, @Param("delta") BigDecimal delta);
}

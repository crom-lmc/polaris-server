package com.laimengchao.sso.ehr.module.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    /** delta 可为负：调整后总额度不得低于已用 + 冻结；人工调整后标记 MANUAL，自动补差不覆盖 */
    @Update("UPDATE ehr_leave_quota SET total_days = total_days + #{delta}, source = 'MANUAL', "
            + "version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND total_days + #{delta} >= used_days + frozen_days")
    int adjust(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /** 自动生成场景（年假跨档补差）：只加不减，并覆盖生成依据快照 */
    @Update("UPDATE ehr_leave_quota SET total_days = total_days + #{delta}, calc_detail = #{detail}, "
            + "version = version + 1 WHERE id = #{id} AND deleted = 0")
    int increaseTotal(@Param("id") Long id, @Param("delta") BigDecimal delta,
                      @Param("detail") String detail);

    /**
     * 加班结算等场景按自然年建额度行。
     * 显式写全唯一键相关列（period_start/period_end），避免依赖数据库的宽松模式。
     */
    @Insert("INSERT INTO ehr_leave_quota (id, employee_id, leave_type, `year`, period_start, period_end, "
            + "period_no, total_days, used_days, frozen_days, source, calc_detail, version, deleted) "
            + "VALUES (#{id}, #{employeeId}, #{leaveType}, #{year}, #{periodStart}, #{periodEnd}, 1, "
            + "0, 0, 0, 'AUTO', #{calcDetail}, 0, 0)")
    int insertNaturalYearQuota(@Param("id") Long id,
                               @Param("employeeId") Long employeeId,
                               @Param("leaveType") String leaveType,
                               @Param("year") Integer year,
                               @Param("periodStart") LocalDate periodStart,
                               @Param("periodEnd") LocalDate periodEnd,
                               @Param("calcDetail") String calcDetail);
}

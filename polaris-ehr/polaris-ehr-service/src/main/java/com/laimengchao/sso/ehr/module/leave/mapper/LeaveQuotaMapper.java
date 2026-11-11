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

    /** 每日重算 AUTO 额度：直接覆盖总额度与生成依据，仅在数值有变化时更新 */
    @Update("UPDATE ehr_leave_quota SET total_days = #{total}, calc_detail = #{detail}, "
            + "version = version + 1 WHERE id = #{id} AND deleted = 0 AND total_days <> #{total}")
    int recalcTotal(@Param("id") Long id, @Param("total") BigDecimal total,
                    @Param("detail") String detail);

    /** 消耗优先扣结转：冻结时同步增加 carryover_used（不超过结转剩余） */
    @Update("UPDATE ehr_leave_quota SET frozen_days = frozen_days + #{days}, "
            + "carryover_used = carryover_used + #{carryDelta}, version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND total_days - used_days - frozen_days >= #{days}")
    int freezeWithCarryover(@Param("id") Long id, @Param("days") BigDecimal days,
                            @Param("carryDelta") BigDecimal carryDelta);

    /** 释放冻结：优先释放当年部分，再释放结转占用（carryover_used 相应减少） */
    @Update("UPDATE ehr_leave_quota SET frozen_days = frozen_days - #{days}, "
            + "carryover_used = carryover_used - #{carryDelta}, version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND frozen_days >= #{days} AND carryover_used >= #{carryDelta}")
    int releaseWithCarryover(@Param("id") Long id, @Param("days") BigDecimal days,
                             @Param("carryDelta") BigDecimal carryDelta);

    /** 销假退回已扣减额度：已用天数减少，结转占用同步释放（审批通过后额度已从 frozen 转入 used） */
    @Update("UPDATE ehr_leave_quota SET used_days = used_days - #{days}, "
            + "carryover_used = carryover_used - #{carryDelta}, version = version + 1 "
            + "WHERE id = #{id} AND deleted = 0 AND used_days >= #{days} AND carryover_used >= #{carryDelta}")
    int refundUsed(@Param("id") Long id, @Param("days") BigDecimal days,
                   @Param("carryDelta") BigDecimal carryDelta);

    /** 签署放弃结转：总额度减去放弃天数，结转占用同步增加（视同消耗），不低于已用+冻结 */
    @Update("UPDATE ehr_leave_quota SET total_days = total_days - #{days}, carryover_used = carryover_used + #{days}, "
            + "version = version + 1 WHERE id = #{id} AND deleted = 0 "
            + "AND total_days - #{days} >= used_days + frozen_days")
    int waiveCarryover(@Param("id") Long id, @Param("days") BigDecimal days);

    /** 生成新年度额度后，回写上一年实际结转量 */
    @Update("UPDATE ehr_leave_quota SET carryover_out = #{carry} WHERE id = #{id} AND deleted = 0")
    int updateCarryoverOut(@Param("id") Long id, @Param("carry") BigDecimal carry);

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

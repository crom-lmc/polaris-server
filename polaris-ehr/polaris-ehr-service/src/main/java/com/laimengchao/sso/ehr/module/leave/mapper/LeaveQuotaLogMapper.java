package com.laimengchao.sso.ehr.module.leave.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuotaLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LeaveQuotaLogMapper extends BaseMapper<LeaveQuotaLog> {

    /**
     * 幂等写入流水：biz_key 唯一，返回 1 表示本次真正写入（可以继续改额度），
     * 返回 0 表示重复投递 / 已处理过，调用方直接跳过额度变更。
     */
    @Insert("INSERT IGNORE INTO ehr_leave_quota_log "
            + "(id, quota_id, employee_id, leave_id, biz_key, action, days, before_available, after_available, "
            + "operator_id, remark, deleted, created_at) VALUES "
            + "(#{id}, #{quotaId}, #{employeeId}, #{leaveId}, #{bizKey}, #{action}, #{days}, "
            + "#{beforeAvailable}, #{afterAvailable}, #{operatorId}, #{remark}, 0, NOW())")
    int insertIgnore(LeaveQuotaLog log);
}

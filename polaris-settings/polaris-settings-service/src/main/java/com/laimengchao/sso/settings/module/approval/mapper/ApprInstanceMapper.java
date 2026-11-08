package com.laimengchao.sso.settings.module.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.settings.module.approval.entity.ApprInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApprInstanceMapper extends BaseMapper<ApprInstance> {

    /**
     * v5：审批前对实例行加排他锁。
     * 多层分支下，两个分支的最后一条任务可能在同一时刻通过，都会尝试触发汇合；
     * 不串行化就会重复激活主干下一步（重复派单）。必须在读任务状态之前先锁。
     */
    @Select("SELECT * FROM appr_instance WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ApprInstance selectForUpdate(@Param("id") Long id);
}

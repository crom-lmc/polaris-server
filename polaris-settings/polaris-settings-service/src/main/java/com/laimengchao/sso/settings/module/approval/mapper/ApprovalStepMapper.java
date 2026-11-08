package com.laimengchao.sso.settings.module.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalStep;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApprovalStepMapper extends BaseMapper<ApprovalStep> {

    /**
     * 物理删除某模板下的全部步骤。
     * <p>
     * 保存模板是「整树替换」，用逻辑删除会踩唯一索引 uk(template_id, seq, deleted)：
     * 反复改几次就会有多个 deleted=1 的同 seq 记录互相冲突。步骤是纯配置，
     * 没有审计价值（流水在 appr_action），物理删掉最省事。
     */
    @Delete("DELETE FROM cfg_approval_step WHERE template_id = #{templateId}")
    int purgeByTemplate(@Param("templateId") Long templateId);
}

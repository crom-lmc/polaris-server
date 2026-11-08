package com.laimengchao.sso.settings.module.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.settings.module.approval.entity.ApprovalNode;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApprovalNodeMapper extends BaseMapper<ApprovalNode> {

    /** 物理删除某模板下的全部分支，理由同 ApprovalStepMapper#purgeByTemplate */
    @Delete("DELETE FROM cfg_approval_node WHERE template_id = #{templateId}")
    int purgeByTemplate(@Param("templateId") Long templateId);
}

package com.laimengchao.sso.ehr.module.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrgNodeMapper extends BaseMapper<OrgNode> {

    /**
     * 组织编码生成用的行锁：锁住表里 id 最小的一行（不管是否已逻辑删除）。
     * <p>
     * 自定义 @Select 不会被 MyBatis-Plus 逻辑删除拦截器追加 deleted=0，所以锁的是全表；
     * 新增根节点和新增子节点都走同一把锁，事务提交时才释放，保证并发新增不会取到同一个序号。
     * 组织新增是低频操作，全表串行化的代价可以接受，比另建一张「编码序列表」轻得多。
     */
    @Select("SELECT id FROM ehr_org_node ORDER BY id ASC LIMIT 1 FOR UPDATE")
    Long lockForOrgCode();

    /**
     * 取某个前缀下已用过的编码（含逻辑删除数据）。
     * <p>
     * 刻意查全表：编码一经生成<b>永不复用</b>，节点被逻辑删除后序号也不退回，
     * 否则「删掉 0002 再新增」会拿到一个历史上已经发出去的编码，外部对接会认错人。
     */
    @Select("SELECT code FROM ehr_org_node WHERE code LIKE CONCAT(#{prefix}, '%')")
    List<String> selectCodesByPrefix(@Param("prefix") String prefix);
}

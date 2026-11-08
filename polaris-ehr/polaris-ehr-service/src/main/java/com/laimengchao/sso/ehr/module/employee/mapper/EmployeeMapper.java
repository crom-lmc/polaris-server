package com.laimengchao.sso.ehr.module.employee.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    /**
     * 组织树上每个人数角标：一次 group by，避免逐节点 count。
     * cnt 只统计试用期(30)/在职(40) —— 编制口径；
     * cntAll 统计除已离职(60)以外的全部员工 —— 删除/禁用的校验口径。
     */
    @Select("SELECT org_id AS orgId, "
            + "SUM(CASE WHEN status IN (30, 40) THEN 1 ELSE 0 END) AS cnt, "
            + "COUNT(*) AS cntAll FROM ehr_employee "
            + "WHERE deleted = 0 AND status <> 60 GROUP BY org_id")
    List<Map<String, Object>> countGroupByOrg();

    /**
     * 自动工号取号用：当前最大的「字母前缀 + 4 位序号」工号（如 A0001）。
     * 不带 deleted = 0——唯一索引含已逻辑删除的历史工号，跳号必须连历史一起跳，
     * 否则生成的号会撞历史档案。REGEXP BINARY 保证只认大写字母前缀，手动录的其他编码格式不参与。
     */
    @Select("SELECT emp_no FROM ehr_employee "
            + "WHERE emp_no REGEXP BINARY '^[A-Z][0-9]{4}$' "
            + "ORDER BY emp_no DESC LIMIT 1")
    String selectLatestGeneratedEmpNo();
}

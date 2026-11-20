package com.laimengchao.sso.forum.module.experience.mapper;

import com.laimengchao.sso.forum.module.experience.entity.ExperienceRule;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExperienceRuleMapper {

    List<ExperienceRule> listAll();

    List<ExperienceRule> listEnabled();

    ExperienceRule findById(@Param("id") long id);

    ExperienceRule findByKey(@Param("ruleKey") String ruleKey);

    int insert(ExperienceRule values);

    int update(ExperienceRule values);

    int delete(@Param("id") long id);
}

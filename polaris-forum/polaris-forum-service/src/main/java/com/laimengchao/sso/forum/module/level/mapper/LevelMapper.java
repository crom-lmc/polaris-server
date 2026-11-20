package com.laimengchao.sso.forum.module.level.mapper;

import com.laimengchao.sso.forum.module.level.entity.Level;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LevelMapper {

    List<Level> listAll();

    List<Level> listEnabled();

    Level findById(@Param("id") long id);

    Level findByNo(@Param("levelNo") int levelNo);

    Level findForUpdate(@Param("id") long id);

    int insert(Level values);

    int update(Level values);

    int delete(@Param("id") long id);
}

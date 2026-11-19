package com.laimengchao.sso.forum.module.draft.mapper;

import com.laimengchao.sso.forum.module.draft.entity.Draft;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DraftMapper {

    int insert(Draft values);

    List<Draft> list(@Param("accountId") long accountId);

    Draft find(@Param("id") long id, @Param("accountId") long accountId);

    Draft findForUpdate(@Param("id") long id, @Param("accountId") long accountId);

    int update(Draft values);

    int delete(@Param("id") long id, @Param("accountId") long accountId);

    int markPublished(@Param("id") long id, @Param("threadId") long threadId);
}

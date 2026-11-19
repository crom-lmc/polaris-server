package com.laimengchao.sso.forum.module.interaction.mapper;

import com.laimengchao.sso.forum.module.interaction.dto.FavoriteThreadDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FavoriteMapper {

    int add(@Param("id") long id, @Param("accountId") long accountId, @Param("threadId") long threadId);

    int remove(@Param("accountId") long accountId, @Param("threadId") long threadId);

    List<FavoriteThreadDTO> list(@Param("accountId") long accountId);

    int count(@Param("accountId") long accountId);
}

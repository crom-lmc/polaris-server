package com.laimengchao.sso.forum.module.taxonomy.mapper;

import com.laimengchao.sso.forum.module.taxonomy.entity.Board;
import com.laimengchao.sso.forum.module.taxonomy.entity.Tag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TaxonomyMapper {

    List<Board> listBoards(@Param("enabledOnly") boolean enabledOnly);

    Board findBoard(@Param("boardKey") String boardKey);

    int insertBoard(Board values);

    int updateBoard(Board values);

    List<Tag> listTags(@Param("boardKey") String boardKey, @Param("enabledOnly") boolean enabledOnly);

    Tag findTag(@Param("id") long id);

    Tag findTagByName(@Param("boardKey") String boardKey, @Param("name") String name);

    int insertTag(Tag values);

    int registerTag(@Param("id") long id, @Param("boardKey") String boardKey, @Param("name") String name);

    int updateTag(Tag values);

    List<String> listThreadTags(@Param("boardKey") String boardKey);
}

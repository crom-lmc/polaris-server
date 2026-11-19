package com.laimengchao.sso.forum.module.admin.mapper;

import com.laimengchao.sso.forum.module.forum.dto.ForumCommentDetailDTO;
import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AdminMapper {

    List<ForumThread> listThreads(@Param("kind") String kind,
                                  @Param("board") String board, @Param("keyword") String keyword,
                                  @Param("deleted") Boolean deleted);

    int setThreadDeleted(@Param("id") long id, @Param("deleted") boolean deleted);

    int setThreadPinned(@Param("id") long id, @Param("pinned") boolean pinned);

    int setThreadSolved(@Param("id") long id, @Param("solved") boolean solved);

    int setThreadRecommended(@Param("id") long id, @Param("recommended") boolean recommended);

    List<ForumCommentDetailDTO> listComments(@Param("threadId") Long threadId, @Param("keyword") String keyword,
                                             @Param("deleted") Boolean deleted);

    ForumCommentDetailDTO findComment(@Param("id") long id);

    ForumComment commentForUpdate(@Param("id") long id);

    int setCommentDeleted(@Param("id") long id, @Param("deleted") boolean deleted);

    int changeReplies(@Param("threadId") long threadId, @Param("delta") int delta);

    List<ForumExchange> listExchanges(@Param("status") String status);

    ForumExchange exchangeForUpdate(@Param("id") long id);

    int ship(@Param("id") long id, @Param("trackingNo") String trackingNo, @Param("remark") String remark);

    int issue(@Param("id") long id, @Param("issuedBy") long issuedBy,
              @Param("issuedByName") String issuedByName, @Param("remark") String remark);

    ForumExchange findExchange(@Param("id") long id);
}

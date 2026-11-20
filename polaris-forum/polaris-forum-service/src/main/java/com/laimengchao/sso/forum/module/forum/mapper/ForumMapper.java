package com.laimengchao.sso.forum.module.forum.mapper;

import com.laimengchao.sso.forum.module.forum.entity.ForumComment;
import com.laimengchao.sso.forum.module.forum.entity.ForumExchange;
import com.laimengchao.sso.forum.module.forum.entity.ForumGift;
import com.laimengchao.sso.forum.module.forum.entity.ForumLotteryLog;
import com.laimengchao.sso.forum.module.forum.entity.ForumMember;
import com.laimengchao.sso.forum.module.forum.entity.ForumPointLog;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ForumMapper {

    List<ForumThread> listThreads(@Param("kind") String kind,
                                  @Param("board") String board, @Param("keyword") String keyword,
                                  @Param("sort") String sort, @Param("accountId") Long accountId);

    ForumThread findThread(@Param("id") long id);

    ForumThread findThreadAny(@Param("id") long id);

    int countBoard(@Param("board") String board);

    int increaseViews(@Param("id") long id);

    int insertThread(ForumThread values);

    List<ForumComment> listComments(@Param("threadId") long threadId);

    ForumComment findComment(@Param("id") long id);

    int insertComment(ForumComment values);

    int increaseReplies(@Param("id") long id);

    int insertLike(@Param("id") long id, @Param("accountId") long accountId, @Param("targetType") String targetType, @Param("targetId") long targetId);

    int deleteLike(@Param("accountId") long accountId, @Param("targetType") String targetType, @Param("targetId") long targetId);

    int likeExists(@Param("accountId") long accountId, @Param("targetType") String targetType, @Param("targetId") long targetId);

    int favoriteExists(@Param("accountId") long accountId, @Param("threadId") long threadId);

    int changeThreadLikes(@Param("id") long id, @Param("delta") int delta);

    int changeCommentLikes(@Param("id") long id, @Param("delta") int delta);

    int changeAuthorLikes(@Param("accountId") long accountId, @Param("delta") int delta);

    int ensureMember(@Param("accountId") long accountId, @Param("username") String username);

    ForumMember memberForUpdate(@Param("accountId") long accountId);

    ForumMember findMember(@Param("accountId") long accountId);

    int changePoints(@Param("accountId") long accountId, @Param("delta") int delta);

    int increaseMemberThreads(@Param("accountId") long accountId);

    int increaseExperience(@Param("accountId") long accountId, @Param("delta") int delta);

    int insertPointLog(ForumPointLog values);

    List<ForumPointLog> pointLogs(@Param("accountId") long accountId);

    int checkin(@Param("id") long id, @Param("accountId") long accountId);

    int checkedInToday(@Param("accountId") long accountId);

    int checkinDays(@Param("accountId") long accountId);

    int lotteryCountToday(@Param("accountId") long accountId);

    int insertLottery(ForumLotteryLog values);

    List<ForumGift> listGifts();

    /** 管理端礼品列表：含下架礼品，按 points/id 排序。 */
    List<ForumGift> adminListGifts();

    ForumGift giftForUpdate(@Param("id") long id);

    ForumGift findGiftById(@Param("id") long id);

    int insertGift(ForumGift values);

    int updateGift(ForumGift values);

    int decreaseGiftStock(@Param("id") long id);

    int insertExchange(ForumExchange values);

    List<ForumExchange> listExchanges(@Param("accountId") long accountId);
}

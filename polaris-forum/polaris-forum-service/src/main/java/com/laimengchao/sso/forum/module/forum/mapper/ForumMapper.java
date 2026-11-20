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

    List<ForumThread> listThreadsPaged(@Param("kind") String kind,
                                       @Param("board") String board, @Param("keyword") String keyword,
                                       @Param("sort") String sort, @Param("accountId") Long accountId,
                                       @Param("offset") long offset, @Param("size") int size);

    long countThreads(@Param("kind") String kind,
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

    /** 批量查询已点赞的 targetId，替代列表里逐条 likeExists 的 N+1。 */
    List<Long> likeExistsBatch(@Param("accountId") long accountId, @Param("targetType") String targetType,
                               @Param("targetIds") List<Long> targetIds);

    int favoriteExists(@Param("accountId") long accountId, @Param("threadId") long threadId);

    int changeThreadLikes(@Param("id") long id, @Param("delta") int delta);

    int changeCommentLikes(@Param("id") long id, @Param("delta") int delta);

    int changeAuthorLikes(@Param("accountId") long accountId, @Param("delta") int delta);

    int ensureMember(@Param("accountId") long accountId, @Param("username") String username);

    ForumMember memberForUpdate(@Param("accountId") long accountId);

    ForumMember findMember(@Param("accountId") long accountId);

    /** 按账号 ID 批量取会员，替代列表里逐条 findMember 的 N+1。 */
    List<ForumMember> findMembers(@Param("accountIds") List<Long> accountIds);

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

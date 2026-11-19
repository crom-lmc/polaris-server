package com.laimengchao.sso.forum.module.poll.mapper;

import com.laimengchao.sso.forum.module.poll.entity.Poll;
import com.laimengchao.sso.forum.module.poll.entity.PollOption;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PollMapper {

    int insertPoll(Poll values);

    int insertOption(PollOption values);

    Poll findByThread(@Param("threadId") long threadId);

    Poll findByThreadForUpdate(@Param("threadId") long threadId);

    List<PollOption> options(@Param("pollId") long pollId);

    int optionBelongs(@Param("pollId") long pollId, @Param("optionId") long optionId);

    int countAccountVotes(@Param("pollId") long pollId, @Param("accountId") long accountId);

    List<Long> selectedOptionIds(@Param("pollId") long pollId, @Param("accountId") long accountId);

    int totalVotes(@Param("pollId") long pollId);

    int insertVote(@Param("id") long id, @Param("pollId") long pollId, @Param("accountId") long accountId, @Param("optionId") long optionId);
}

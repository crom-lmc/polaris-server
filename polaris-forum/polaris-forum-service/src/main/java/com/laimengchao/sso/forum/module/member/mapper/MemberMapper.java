package com.laimengchao.sso.forum.module.member.mapper;

import com.laimengchao.sso.forum.module.forum.entity.ForumMember;
import com.laimengchao.sso.forum.module.forum.entity.ForumThread;
import com.laimengchao.sso.forum.module.member.dto.MemberDetailDTO;
import com.laimengchao.sso.forum.module.member.dto.MemberStatsDTO;
import com.laimengchao.sso.forum.module.member.dto.PointRankingDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MemberMapper {

    MemberDetailDTO findById(@Param("accountId") long accountId);

    int updateProfile(ForumMember values);

    MemberStatsDTO stats(@Param("accountId") long accountId);

    List<PointRankingDTO> pointRanking(@Param("limit") int limit);

    List<PointRankingDTO> levelRanking(@Param("limit") int limit);

    List<ForumThread> memberThreads(@Param("accountId") long accountId, @Param("kind") String kind);
}

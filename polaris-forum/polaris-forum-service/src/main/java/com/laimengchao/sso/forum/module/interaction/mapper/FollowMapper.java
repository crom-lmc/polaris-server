package com.laimengchao.sso.forum.module.interaction.mapper;

import com.laimengchao.sso.forum.module.interaction.dto.FollowRelationDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FollowMapper {

    int add(@Param("accountId") long accountId, @Param("followedAccountId") long followedAccountId);

    int remove(@Param("accountId") long accountId, @Param("followedAccountId") long followedAccountId);

    int exists(@Param("accountId") long accountId, @Param("followedAccountId") long followedAccountId);

    int followingCount(@Param("accountId") long accountId);

    int followerCount(@Param("accountId") long accountId);

    List<FollowRelationDTO> following(@Param("accountId") long accountId);

    List<FollowRelationDTO> followers(@Param("accountId") long accountId);
}

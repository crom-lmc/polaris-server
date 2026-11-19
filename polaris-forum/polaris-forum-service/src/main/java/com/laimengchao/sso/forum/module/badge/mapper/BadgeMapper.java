package com.laimengchao.sso.forum.module.badge.mapper;

import com.laimengchao.sso.forum.module.badge.dto.BadgeAcquiredDTO;
import com.laimengchao.sso.forum.module.badge.dto.BadgeAdminDTO;
import com.laimengchao.sso.forum.module.badge.dto.BadgeMemberDTO;
import com.laimengchao.sso.forum.module.badge.entity.Badge;
import com.laimengchao.sso.forum.module.badge.entity.MemberBadge;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BadgeMapper {

    int insert(Badge values);

    List<BadgeAdminDTO> listAdmin(@Param("status") String status);

    Badge findById(@Param("id") long id);

    Badge findForUpdate(@Param("id") long id);

    List<Badge> listPublished();

    List<Badge> listPublishedForRules(@Param("ruleTypes") List<String> ruleTypes);

    int updateDraft(Badge values);

    int publish(@Param("id") long id);

    int offline(@Param("id") long id);

    List<Long> listAccountIds();

    int award(MemberBadge values);

    List<BadgeAcquiredDTO> listAcquired(@Param("accountId") long accountId, @Param("limit") int limit);

    List<BadgeMemberDTO> listForMember(@Param("accountId") long accountId);

    long totalPoints(@Param("accountId") long accountId);

    long threadCount(@Param("accountId") long accountId);

    long commentCount(@Param("accountId") long accountId);

    long checkinDays(@Param("accountId") long accountId);

    long likesReceived(@Param("accountId") long accountId);
}

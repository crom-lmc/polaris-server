package com.laimengchao.sso.forum.module.interaction.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.interaction.dto.FollowRelationDTO;
import com.laimengchao.sso.forum.module.interaction.mapper.FollowMapper;
import com.laimengchao.sso.forum.module.interaction.vo.FollowRelationVO;
import com.laimengchao.sso.forum.module.interaction.vo.FollowToggleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowMapper mapper;
    private final ForumMapper forumMapper;

    public FollowToggleVO follow(long accountId) {
        SecurityUser user = requireUser();
        requireTarget(accountId);
        if (user.getAccountId() == accountId) throw param("不能关注自己");
        mapper.add(user.getAccountId(), accountId);
        return new FollowToggleVO(true);
    }

    public FollowToggleVO unfollow(long accountId) {
        SecurityUser user = requireUser();
        requireTarget(accountId);
        mapper.remove(user.getAccountId(), accountId);
        return new FollowToggleVO(false);
    }

    public List<FollowRelationVO> relations(long accountId, String type) {
        return switch (type == null ? "" : type.trim().toLowerCase()) {
            case "followers" -> followers(accountId);
            case "following" -> following(accountId);
            default -> throw param("关系类型仅支持 followers 或 following");
        };
    }

    public List<FollowRelationVO> following(long accountId) {
        requireTarget(accountId);
        return mapper.following(accountId).stream().map(this::memberView).toList();
    }

    public List<FollowRelationVO> followers(long accountId) {
        requireTarget(accountId);
        return mapper.followers(accountId).stream().map(this::memberView).toList();
    }

    public int followingCount(long accountId) {
        return mapper.followingCount(accountId);
    }

    public int followerCount(long accountId) {
        return mapper.followerCount(accountId);
    }

    public boolean isFollowing(long accountId, long followedAccountId) {
        return accountId > 0 && mapper.exists(accountId, followedAccountId) > 0;
    }

    private FollowRelationVO memberView(FollowRelationDTO row) {
        FollowRelationVO view = new FollowRelationVO();
        view.setAccountId(row.getAccountId());
        view.setUsername(row.getUsername());
        view.setNickname(row.getNickname());
        view.setBio(row.getBio());
        view.setLocation(row.getLocation());
        view.setWebsite(row.getWebsite());
        view.setAvatarAttachmentId(row.getAvatarAttachmentId());
        view.setFollowedAt(row.getFollowedAt());
        String nickname = text(row.getNickname()).trim();
        view.setDisplayName(nickname.isBlank() ? text(row.getUsername()) : nickname);
        long avatarId = longValue(row.getAvatarAttachmentId());
        view.setAvatarUrl(avatarId > 0 ? "/api/forum/public/attachments/" + avatarId : null);
        return view;
    }

    private void requireTarget(long accountId) {
        if (accountId <= 0 || forumMapper.findMember(accountId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "社区成员不存在");
        }
    }

    private SecurityUser requireUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    private BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null || String.valueOf(value).isBlank() ? 0 : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }
}

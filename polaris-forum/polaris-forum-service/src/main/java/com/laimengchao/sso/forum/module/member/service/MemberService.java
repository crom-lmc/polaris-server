package com.laimengchao.sso.forum.module.member.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.forum.module.attachment.service.AttachmentService;
import com.laimengchao.sso.forum.module.badge.mapper.BadgeMapper;
import com.laimengchao.sso.forum.module.forum.entity.ForumMember;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.interaction.service.FavoriteService;
import com.laimengchao.sso.forum.module.interaction.service.FollowService;
import com.laimengchao.sso.forum.module.member.dto.MemberDetailDTO;
import com.laimengchao.sso.forum.module.member.dto.MemberProfileUpdateRequest;
import com.laimengchao.sso.forum.module.member.dto.MemberStatsDTO;
import com.laimengchao.sso.forum.module.member.dto.PointRankingDTO;
import com.laimengchao.sso.forum.module.member.mapper.MemberMapper;
import com.laimengchao.sso.forum.module.member.vo.MemberProfileVO;
import com.laimengchao.sso.forum.module.member.vo.MemberVO;
import com.laimengchao.sso.forum.module.member.vo.PointRankingVO;
import com.laimengchao.sso.forum.module.member.vo.PublicMemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MemberService {

    private static final Set<String> KINDS = Set.of("feed", "topic", "question", "article");

    private final MemberMapper mapper;
    private final ForumMapper forumMapper;
    private final ForumService forumService;
    private final AttachmentService attachmentService;
    private final BadgeMapper badgeMapper;
    private final FavoriteService favoriteService;
    private final FollowService followService;

    public PublicMemberVO publicMember(long accountId) {
        MemberDetailDTO member = mapper.findById(accountId);
        if (member == null) throw new BizException(ErrorCode.NOT_FOUND, "社区成员不存在");
        PublicMemberVO view = memberView(member, new PublicMemberVO());
        applyStats(view, mapper.stats(accountId));
        view.setBadges(badgeMapper.listAcquired(accountId, 12));
        view.setFollowingCount(followService.followingCount(accountId));
        view.setFollowersCount(followService.followerCount(accountId));
        Long currentAccountId = SecurityUtils.currentUserId();
        view.setFollowing(currentAccountId != null && followService.isFollowing(currentAccountId, accountId));
        return view;
    }

    public List<ThreadVO> memberThreads(long accountId, String kind) {
        if (mapper.findById(accountId) == null) throw new BizException(ErrorCode.NOT_FOUND, "社区成员不存在");
        String value = kind == null ? "" : kind.trim().toLowerCase();
        if (!value.isBlank() && !KINDS.contains(value)) throw param("不支持的内容类型");
        return mapper.memberThreads(accountId, value).stream().map(forumService::threadView).toList();
    }

    public List<PointRankingVO> pointRanking(Integer limit) {
        int size = limit == null ? 10 : limit;
        if (size < 1 || size > 50) throw param("排行数量范围为 1-50");
        return mapper.pointRanking(size).stream().map(row -> {
            PointRankingVO view = new PointRankingVO();
            view.setAccountId(row.getAccountId());
            view.setName(displayName(row.getNickname(), row.getUsername()));
            view.setPoints(longValue(row.getPoints()));
            view.setTotalPoints(longValue(row.getTotalPoints()));
            view.setLevel(level(longValue(row.getTotalPoints())));
            long avatarId = longValue(row.getAvatarAttachmentId());
            view.setAvatarUrl(avatarId > 0 ? "/api/forum/public/attachments/" + avatarId : null);
            return view;
        }).toList();
    }

    public MemberProfileVO profile() {
        SecurityUser user = requireUser();
        forumMapper.ensureMember(user.getAccountId(), user.getUsername());
        MemberProfileVO view = memberView(mapper.findById(user.getAccountId()), new MemberProfileVO());
        applyStats(view, mapper.stats(user.getAccountId()));
        view.setFavoriteCount(favoriteService.count(user.getAccountId()));
        view.setBadgeCount(badgeMapper.listAcquired(user.getAccountId(), 1000).size());
        return view;
    }

    @Transactional
    public MemberProfileVO updateProfile(MemberProfileUpdateRequest body) {
        SecurityUser user = requireUser();
        forumMapper.ensureMember(user.getAccountId(), user.getUsername());
        MemberDetailDTO current = mapper.findById(user.getAccountId());
        String nickname = value(body.getNickname(), body.isNicknamePresent(), current.getNickname(), "nickname", 80);
        String bio = value(body.getBio(), body.isBioPresent(), current.getBio(), "bio", 500);
        String location = value(body.getLocation(), body.isLocationPresent(), current.getLocation(), "location", 100);
        String website = value(body.getWebsite(), body.isWebsitePresent(), current.getWebsite(), "website", 255);
        validateWebsite(website);
        Long avatarId = attachmentId(body.getAvatarAttachmentId(), body.isAvatarAttachmentIdPresent(),
                current.getAvatarAttachmentId(), "avatarAttachmentId");
        Long coverId = attachmentId(body.getCoverAttachmentId(), body.isCoverAttachmentIdPresent(),
                current.getCoverAttachmentId(), "coverAttachmentId");
        attachmentService.bindProfileImages(user.getAccountId(), avatarId, coverId,
                body.isAvatarAttachmentIdPresent(), body.isCoverAttachmentIdPresent());
        ForumMember values = new ForumMember();
        values.setAccountId(user.getAccountId());
        values.setNickname(nickname);
        values.setBio(bio);
        values.setLocation(location);
        values.setWebsite(website);
        values.setAvatarAttachmentId(avatarId);
        values.setCoverAttachmentId(coverId);
        mapper.updateProfile(values);
        return profile();
    }

    private <T extends MemberVO> T memberView(MemberDetailDTO row, T view) {
        view.setAccountId(row.getAccountId());
        view.setUsername(row.getUsername());
        view.setNickname(row.getNickname());
        view.setBio(row.getBio());
        view.setLocation(row.getLocation());
        view.setWebsite(row.getWebsite());
        view.setPoints(row.getPoints());
        view.setThreadsCount(longValue(row.getThreadsCount()));
        view.setLikesReceived(longValue(row.getLikesReceived()));
        view.setAvatarAttachmentId(row.getAvatarAttachmentId());
        view.setCoverAttachmentId(row.getCoverAttachmentId());
        view.setCreatedAt(row.getCreatedAt());
        view.setDisplayName(displayName(row.getNickname(), row.getUsername()));
        long avatarId = longValue(row.getAvatarAttachmentId());
        long coverId = longValue(row.getCoverAttachmentId());
        view.setAvatarUrl(avatarId > 0 ? "/api/forum/public/attachments/" + avatarId : null);
        view.setCoverUrl(coverId > 0 ? "/api/forum/public/attachments/" + coverId : null);
        return view;
    }

    private void applyStats(MemberVO view, MemberStatsDTO stats) {
        view.setThreadsCount(stats.getThreadsCount());
        view.setCommentsCount(stats.getCommentsCount());
        view.setLikesReceived(stats.getLikesReceived());
        view.setTotalPoints(stats.getTotalPoints());
        view.setLevel(level(longValue(stats.getTotalPoints())));
    }

    private String displayName(String nickname, String username) {
        String value = text(nickname).trim();
        return value.isBlank() ? text(username) : value;
    }

    private int level(long totalPoints) {
        return (int) Math.max(1, totalPoints / 500 + 1);
    }

    private void validateWebsite(String website) {
        if (website.isBlank()) return;
        try {
            URI uri = URI.create(website);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                throw param("个人主页仅支持有效的 http/https 地址");
            }
        } catch (IllegalArgumentException e) {
            throw param("个人主页仅支持有效的 http/https 地址");
        }
    }

    private String value(Object requested, boolean present, String current, String key, int maxLength) {
        String value = text(present ? requested : current).trim();
        if (value.length() > maxLength) throw param(key + " 长度不能超过 " + maxLength + " 个字符");
        return value;
    }

    private Long attachmentId(Object requested, boolean present, Long current, String key) {
        Object raw = present ? requested : current;
        if (raw == null || String.valueOf(raw).isBlank()) return null;
        long id = longValue(raw);
        if (id <= 0) throw param(key + " 不正确");
        return id;
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

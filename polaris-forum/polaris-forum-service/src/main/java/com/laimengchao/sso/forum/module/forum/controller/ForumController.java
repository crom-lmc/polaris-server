package com.laimengchao.sso.forum.module.forum.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.module.badge.service.BadgeService;
import com.laimengchao.sso.forum.module.badge.vo.MyBadgeVO;
import com.laimengchao.sso.forum.module.forum.dto.CommentCreateRequest;
import com.laimengchao.sso.forum.module.forum.dto.LikeToggleRequest;
import com.laimengchao.sso.forum.module.forum.dto.ThreadPublishRequest;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.CommentVO;
import com.laimengchao.sso.forum.module.forum.vo.ForumMeVO;
import com.laimengchao.sso.forum.module.forum.vo.LikeToggleVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import com.laimengchao.sso.forum.module.forum.vo.ThreadVO;
import com.laimengchao.sso.forum.module.interaction.service.FavoriteService;
import com.laimengchao.sso.forum.module.interaction.service.FollowService;
import com.laimengchao.sso.forum.module.interaction.vo.FavoriteToggleVO;
import com.laimengchao.sso.forum.module.interaction.vo.FollowToggleVO;
import com.laimengchao.sso.forum.module.member.dto.MemberProfileUpdateRequest;
import com.laimengchao.sso.forum.module.member.service.MemberService;
import com.laimengchao.sso.forum.module.member.vo.MemberProfileVO;
import com.laimengchao.sso.forum.module.report.dto.ReportCreateRequest;
import com.laimengchao.sso.forum.module.report.service.ReportService;
import com.laimengchao.sso.forum.module.report.vo.ReportVO;
import com.laimengchao.sso.forum.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/forum")
@RequiredArgsConstructor
public class ForumController {

    private final ForumService forumService;
    private final MemberService memberService;
    private final FavoriteService favoriteService;
    private final BadgeService badgeService;
    private final FollowService followService;
    private final ReportService reportService;

    @GetMapping("/me")
    public R<ForumMeVO> me() {
        return R.ok(forumService.me());
    }

    @PostMapping("/threads")
    @Permit(value = PermissionCodes.FORUM_POST_CREATE, app = PermissionCodes.APP_FORUM)
    public R<ThreadDetailVO> createThread(@RequestBody ThreadPublishRequest body) {
        return R.ok(forumService.createThread(body));
    }

    @PostMapping("/threads/{id}/comments")
    @Permit(value = PermissionCodes.FORUM_COMMENT_CREATE, app = PermissionCodes.APP_FORUM)
    public R<CommentVO> createComment(@PathVariable long id, @RequestBody CommentCreateRequest body) {
        return R.ok(forumService.createComment(id, body));
    }

    @PostMapping("/likes/toggle")
    @Permit(value = PermissionCodes.FORUM_LIKE, app = PermissionCodes.APP_FORUM)
    public R<LikeToggleVO> toggleLike(@RequestBody LikeToggleRequest body) {
        return R.ok(forumService.toggleLike(body));
    }

    @GetMapping("/profile")
    public R<MemberProfileVO> profile() {
        return R.ok(memberService.profile());
    }

    @PutMapping("/profile")
    @Permit(value = PermissionCodes.FORUM_PROFILE_EDIT, app = PermissionCodes.APP_FORUM)
    public R<MemberProfileVO> updateProfile(@RequestBody MemberProfileUpdateRequest body) {
        return R.ok(memberService.updateProfile(body));
    }

    @PostMapping("/threads/{id}/favorite")
    @Permit(value = PermissionCodes.FORUM_FAVORITE, app = PermissionCodes.APP_FORUM)
    public R<FavoriteToggleVO> favorite(@PathVariable long id) {
        return R.ok(favoriteService.add(id));
    }

    @DeleteMapping("/threads/{id}/favorite")
    @Permit(value = PermissionCodes.FORUM_FAVORITE, app = PermissionCodes.APP_FORUM)
    public R<FavoriteToggleVO> unfavorite(@PathVariable long id) {
        return R.ok(favoriteService.remove(id));
    }

    @GetMapping("/favorites")
    public R<List<ThreadVO>> favorites() {
        return R.ok(favoriteService.list());
    }

    @GetMapping("/badges")
    public R<List<MyBadgeVO>> badges() {
        return R.ok(badgeService.myBadges());
    }

    @PostMapping("/reports")
    @Permit(value = PermissionCodes.FORUM_REPORT_CREATE, app = PermissionCodes.APP_FORUM)
    public R<ReportVO> report(@RequestBody ReportCreateRequest body) {
        return R.ok(reportService.create(body));
    }

    @PostMapping("/members/{accountId}/follow")
    @Permit(value = PermissionCodes.FORUM_FOLLOW, app = PermissionCodes.APP_FORUM)
    public R<FollowToggleVO> follow(@PathVariable long accountId) {
        return R.ok(followService.follow(accountId));
    }

    @DeleteMapping("/members/{accountId}/follow")
    @Permit(value = PermissionCodes.FORUM_FOLLOW, app = PermissionCodes.APP_FORUM)
    public R<FollowToggleVO> unfollow(@PathVariable long accountId) {
        return R.ok(followService.unfollow(accountId));
    }
}

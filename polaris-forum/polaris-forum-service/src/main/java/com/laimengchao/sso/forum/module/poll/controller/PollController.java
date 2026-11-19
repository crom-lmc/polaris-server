package com.laimengchao.sso.forum.module.poll.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import com.laimengchao.sso.forum.module.poll.dto.PollVoteRequest;
import com.laimengchao.sso.forum.module.poll.service.PollService;
import com.laimengchao.sso.forum.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forum/threads")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;
    private final ForumService forumService;

    @PostMapping("/{id}/votes")
    @Permit(value = PermissionCodes.FORUM_VOTE, app = PermissionCodes.APP_FORUM)
    public R<ThreadDetailVO> vote(@PathVariable long id, @RequestBody PollVoteRequest body) {
        pollService.vote(id, body);
        return R.ok(forumService.detail(id));
    }
}

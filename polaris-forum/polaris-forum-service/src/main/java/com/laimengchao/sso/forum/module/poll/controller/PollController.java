package com.laimengchao.sso.forum.module.poll.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.forum.service.ForumService;
import com.laimengchao.sso.forum.module.forum.vo.ThreadDetailVO;
import com.laimengchao.sso.forum.module.poll.dto.PollVoteRequest;
import com.laimengchao.sso.forum.module.poll.service.PollService;
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
    public R<ThreadDetailVO> vote(@PathVariable long id, @RequestBody PollVoteRequest body) {
        pollService.vote(id, body);
        return R.ok(forumService.detail(id));
    }
}

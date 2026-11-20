package com.laimengchao.sso.forum.module.member.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.forum.module.forum.mapper.ForumMapper;
import com.laimengchao.sso.forum.module.member.dto.EnsureMemberRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 论坛成员内部接口：供人力服务开户成功后登记成员，使「已开通账号的人」自动计入论坛人数。
 * <p>
 * 幂等：底层 ensureMember 为 INSERT ... ON DUPLICATE KEY UPDATE，重复投递不会报错、不覆盖已有资料。
 * 鉴权：路径 /internal/** 由 common 的 InternalTokenInterceptor 做 fail-closed 准入校验。
 */
@Slf4j
@RestController
@RequestMapping("/internal/forum/members")
@RequiredArgsConstructor
public class InternalMemberController {

    private final ForumMapper mapper;

    @PostMapping("/ensure")
    public R<Void> ensure(@RequestBody EnsureMemberRequest body) {
        if (body == null || body.getAccountId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "accountId 不能为空");
        }
        long accountId;
        try {
            accountId = Long.parseLong(String.valueOf(body.getAccountId()));
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "accountId 非法");
        }
        String username = body.getUsername() == null || body.getUsername().isBlank()
                ? String.valueOf(accountId) : body.getUsername();
        mapper.ensureMember(accountId, username);
        log.info("开户联动登记论坛成员：accountId={} username={}", accountId, username);
        return R.ok();
    }
}

package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.domain.entity.SsoWechatBind;
import com.laimengchao.sso.account.dto.request.BindWechatRequest;
import com.laimengchao.sso.account.service.WechatBindService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.util.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录账户的微信绑定管理（看自己的资源，只需登录，不需额外按钮权限）。
 */
@RestController
@RequestMapping("/api/accounts/wechat")
@RequiredArgsConstructor
public class WechatBindController {

    private final WechatBindService wechatBindService;

    @GetMapping
    public R<List<SsoWechatBind>> list() {
        Long id = requireAccountId();
        return R.ok(wechatBindService.list(id));
    }

    @PostMapping
    public R<Void> bind(@RequestBody BindWechatRequest req) {
        Long id = requireAccountId();
        wechatBindService.bind(id, req.getOpenid(), req.getUnionid(), req.getAppType(),
                req.getNickname(), req.getAvatarUrl());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> unbind(@PathVariable Long id) {
        wechatBindService.unbind(id, requireAccountId());
        return R.ok();
    }

    private Long requireAccountId() {
        Long id = AccountContext.getAccountId();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }
}

package com.laimengchao.sso.account.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.account.domain.entity.SsoAccount;
import com.laimengchao.sso.account.domain.entity.SsoWechatBind;
import com.laimengchao.sso.account.mapper.SsoAccountMapper;
import com.laimengchao.sso.account.mapper.SsoWechatBindMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微信绑定关系。身份凭证（openid/unionid）归属 account，由 sso-auth 在扫码后调用本服务落库/查询。
 */
@Service
@RequiredArgsConstructor
public class WechatBindService {

    private final SsoWechatBindMapper bindMapper;
    private final SsoAccountMapper accountMapper;

    /** 按 openid 取绑定；未绑定返回 null（auth 据此引导用户先绑定）。 */
    public SsoWechatBind findByOpenid(String openid, String appType) {
        return bindMapper.selectByOpenid(openid, appType == null ? "open" : appType);
    }

    /** 按 openid 反查绑定账户ID；未绑定返回 null。 */
    public Long findAccountIdByOpenid(String openid, String appType) {
        SsoWechatBind bind = findByOpenid(openid, appType);
        return bind == null ? null : bind.getAccountId();
    }

    /** 当前登录账户绑定一个微信 identity（幂等：同一 openid 重复绑定覆盖）。 */
    @Transactional(rollbackFor = Exception.class)
    public void bind(Long accountId, String openid, String unionid, String appType,
                     String nickname, String avatarUrl) {
        String type = appType == null ? "open" : appType;
        SsoWechatBind old = bindMapper.selectByOpenid(openid, type);
        if (old != null) {
            if (!old.getAccountId().equals(accountId)) {
                throw new BizException(ErrorCode.CONFLICT, "该微信已绑定其他账户");
            }
            old.setUnionid(unionid);
            old.setNickname(nickname);
            old.setAvatarUrl(avatarUrl);
            bindMapper.updateById(old);
            return;
        }
        SsoWechatBind bind = new SsoWechatBind();
        bind.setAccountId(accountId);
        bind.setOpenid(openid);
        bind.setUnionid(unionid);
        bind.setAppType(type);
        bind.setNickname(nickname);
        bind.setAvatarUrl(avatarUrl);
        bind.setBindAt(LocalDateTime.now());
        bindMapper.insert(bind);
    }

    public List<SsoWechatBind> list(Long accountId) {
        return bindMapper.selectByAccount(accountId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long id, Long accountId) {
        SsoWechatBind bind = bindMapper.selectById(id);
        if (bind == null || !bind.getAccountId().equals(accountId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "绑定关系不存在");
        }
        bindMapper.deleteById(id);
    }

    /** 通过邮箱找到可登录的账户（微信登录需要邮箱已存在；若需要也可改为按 unionid 关联）。 */
    public SsoAccount findAccountByOpenid(String openid, String appType) {
        SsoWechatBind bind = findByOpenid(openid, appType);
        if (bind == null) {
            return null;
        }
        return accountMapper.selectById(bind.getAccountId());
    }
}

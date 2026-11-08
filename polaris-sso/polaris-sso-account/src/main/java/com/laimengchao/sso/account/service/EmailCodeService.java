package com.laimengchao.sso.account.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.account.domain.entity.SsoAccount;
import com.laimengchao.sso.account.domain.entity.SsoEmailCode;
import com.laimengchao.sso.account.mapper.SsoAccountMapper;
import com.laimengchao.sso.account.mapper.SsoEmailCodeMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/**
 * 邮箱验证码存储与校验（只存哈希，禁止明文）。
 * <p>生成与发送由 sso-auth 负责（auth 持有 SMTP 配置）；本服务只负责保存与校验。</p>
 */
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private final SsoEmailCodeMapper codeMapper;
    private final SsoAccountMapper accountMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${sso.account.email-code-ttl-minutes:5}")
    private int ttlMinutes;
    @Value("${sso.account.email-code-max-try:5}")
    private int maxTry;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 生成 6 位验证码，哈希入库，返回明文（供 auth 发送）。 */
    @Transactional(rollbackFor = Exception.class)
    public String create(String email, String scene, String ip) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        SsoEmailCode entity = new SsoEmailCode();
        entity.setEmail(email);
        entity.setCodeHash(passwordEncoder.encode(code));
        entity.setScene(scene == null ? "login" : scene);
        entity.setIp(ip);
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        entity.setUsed(0);
        entity.setTryCount(0);
        entity.setCreatedAt(LocalDateTime.now());
        codeMapper.insert(entity);
        return code;
    }

    /**
     * 校验验证码，返回通过该邮箱登录的账户ID。失败抛业务异常。
     * <p>多次错误会直接作废该条验证码（防爆破）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Long verify(String email, String scene, String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "验证码不能为空");
        }
        SsoEmailCode entity = codeMapper.latestValid(email, scene == null ? "login" : scene);
        if (entity == null) {
            throw new BizException(ErrorCode.BIZ_ERROR, "验证码不存在、已过期或已使用");
        }
        if (passwordEncoder.matches(code, entity.getCodeHash())) {
            entity.setUsed(1);
            codeMapper.updateById(entity);
            SsoAccount account = accountMapper.selectByEmail(email);
            if (account == null || account.getDeleted() != null && account.getDeleted() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "该邮箱未关联任何账户");
            }
            return account.getId();
        }
        int tries = entity.getTryCount() + 1;
        entity.setTryCount(tries);
        if (tries >= maxTry) {
            entity.setUsed(1); // 作废，防止暴力枚举
        }
        codeMapper.updateById(entity);
        throw new BizException(ErrorCode.BIZ_ERROR, "验证码错误");
    }

    /** 让某邮箱下未使用的验证码失效（改绑邮箱等场景可选调用）。 */
    public void invalidate(String email, String scene) {
        codeMapper.update(new LambdaUpdateWrapper<SsoEmailCode>()
                .eq(SsoEmailCode::getEmail, email)
                .eq(SsoEmailCode::getScene, scene == null ? "login" : scene)
                .set(SsoEmailCode::getUsed, 1));
    }
}

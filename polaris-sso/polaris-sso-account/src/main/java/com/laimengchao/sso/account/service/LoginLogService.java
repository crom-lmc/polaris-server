package com.laimengchao.sso.account.service;

import com.laimengchao.sso.account.domain.entity.SsoLoginLog;
import com.laimengchao.sso.account.mapper.SsoLoginLogMapper;
import com.laimengchao.sso.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录审计日志。写入失败不应影响登录本身，因此 record 内部吞掉异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final SsoLoginLogMapper logMapper;

    public void record(String username, Long accountId, String loginType, boolean success,
                       String failReason, String clientIp, String userAgent) {
        try {
            SsoLoginLog log = new SsoLoginLog();
            log.setUsername(username);
            log.setAccountId(accountId);
            log.setLoginType(loginType == null ? "password" : loginType);
            log.setResult(success ? "success" : "fail");
            log.setFailReason(success ? null : failReason);
            log.setClientIp(clientIp);
            log.setUserAgent(userAgent);
            log.setCreatedAt(LocalDateTime.now());
            logMapper.insert(log);
        } catch (Exception e) {
            log.warn("写入登录日志失败（已忽略，不影响登录）: {}", e.getMessage());
        }
    }

    public PageResult<SsoLoginLog> page(int page, int size, String username, String loginType) {
        long offset = (long) (page - 1) * size;
        List<SsoLoginLog> records = logMapper.page(
                blankToNull(username), blankToNull(loginType), offset, size);
        long total = logMapper.count(blankToNull(username), blankToNull(loginType));
        return PageResult.of(records, total, page, size);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}

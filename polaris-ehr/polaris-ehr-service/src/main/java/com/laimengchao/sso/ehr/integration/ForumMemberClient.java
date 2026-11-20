package com.laimengchao.sso.ehr.integration;

import com.laimengchao.sso.client.dto.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 开户联动论坛成员的容错门面。
 * <p>
 * 开户是人事主流程、论坛成员登记是附属动作：论坛暂不可用 / 令牌未配 / 返回失败时，
 * 这里一律只记日志、不抛异常，绝不阻断开户（开户事务已成功，成员可由存量回填补偿）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumMemberClient {

    private final ForumMemberApi forumMemberApi;

    /**
     * 幂等登记论坛成员。任何异常都被吞掉并留痕，保证不影响开户主流程。
     *
     * @param accountId 账号中心返回的账号 ID
     * @param username  登录名（可空，空时由论坛侧兜底）
     */
    public void ensureMember(Long accountId, String username) {
        if (accountId == null) {
            return;
        }
        try {
            ForumMemberRequest req = new ForumMemberRequest();
            req.setAccountId(String.valueOf(accountId));
            req.setUsername(username);
            R<Void> resp = forumMemberApi.ensure(req);
            if (resp == null || !resp.ok()) {
                log.warn("开户联动论坛成员未成功（不阻断开户）：accountId={} resp={}",
                        accountId, resp == null ? "无响应" : resp.getMessage());
            } else {
                log.info("开户联动论坛成员已登记：accountId={}", accountId);
            }
        } catch (FeignException e) {
            log.error("开户联动论坛成员失败（不阻断开户）：accountId={} status={}",
                    accountId, e.status(), e);
        } catch (Exception e) {
            log.error("开户联动论坛成员失败（不阻断开户）：accountId={}", accountId, e);
        }
    }
}

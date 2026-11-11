package com.laimengchao.sso.gateway.schedule;

import com.laimengchao.sso.gateway.support.RevocationCache;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 吊销名单同步：从 sso-auth 拉取已登出的 jti。
 * <p>
 * 触发由调度中心配置（FIXED_DELAY 5s），且必须用 BROADCAST：
 * 吊销名单是每个网关实例的内存缓存，只调一个实例会让其他实例继续放行已登出的令牌。
 */
@Component
@RequiredArgsConstructor
public class RevocationSyncJob implements JobHandler {

    private final RevocationCache revocationCache;

    @Override
    public String name() {
        return "revocationSync";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        revocationCache.sync();
        return JobResult.ok();
    }
}

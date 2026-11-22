package com.laimengchao.sso.openapi.module.channel.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.channel.entity.ApiOutboundChannel;
import com.laimengchao.sso.openapi.module.channel.mapper.ApiOutboundChannelMapper;
import com.laimengchao.sso.openapi.module.channel.vo.ChannelSaveRequest;
import com.laimengchao.sso.openapi.module.channel.vo.ChannelVO;
import com.laimengchao.sso.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 出站渠道服务：渠道 CRUD + 凭证 AES-GCM 托管。
 * <p>
 * 明文凭证只在「写入」这一刻存在于内存：接口收到明文 → 立即加密成 ENC(...) → 覆盖 credential_ref → 明文对象随方法结束被回收。
 * 列表/详情只回显 credentialConfigured 布尔值，杜绝凭证外泄。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ApiOutboundChannelMapper channelMapper;
    private final CredentialCrypto credentialCrypto;

    /** 渠道列表（凭证只回显是否已配置）。 */
    public List<ChannelVO> list() {
        return channelMapper.selectList(Wrappers.<ApiOutboundChannel>lambdaQuery()
                        .orderByAsc(ApiOutboundChannel::getChannelCode))
                .stream().map(this::toVO).toList();
    }

    /** 渠道详情（凭证只回显是否已配置）。 */
    public ChannelVO get(String channelCode) {
        return toVO(require(channelCode));
    }

    /** 新建或按 channel_code 更新渠道基础信息（不动凭证）。 */
    @Transactional(rollbackFor = Exception.class)
    public void save(ChannelSaveRequest req) {
        if (req.getChannelCode() == null || req.getChannelCode().isBlank()) {
            throw new BizException(OpenapiErrorCode.CONFIG_ERROR, "channel_code 必填");
        }
        ApiOutboundChannel existing = channelMapper.selectByCode(req.getChannelCode());
        ApiOutboundChannel channel = existing == null ? new ApiOutboundChannel() : existing;
        channel.setChannelCode(req.getChannelCode());
        channel.setChannelName(req.getChannelName());
        channel.setBaseUrl(req.getBaseUrl() == null ? "" : req.getBaseUrl());
        channel.setAuthType(req.getAuthType() == null ? "NONE" : req.getAuthType());
        channel.setAuthConfig(req.getAuthConfig());
        channel.setTimeoutMs(req.getTimeoutMs() == null ? 5000 : req.getTimeoutMs());
        channel.setRetryMax(req.getRetryMax() == null ? 0 : req.getRetryMax());
        channel.setAllowedApps(req.getAllowedApps());
        channel.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        channel.setRemark(req.getRemark());
        channel.setUpdatedAt(LocalDateTime.now());
        if (existing == null) {
            channel.setCreatedAt(LocalDateTime.now());
            channelMapper.insert(channel);
        } else {
            channelMapper.updateById(channel);
        }
        log.info("出站渠道已保存：channelCode={} 新建={}", channel.getChannelCode(), existing == null);
    }

    /** 启停渠道。 */
    public void toggle(String channelCode, int status) {
        ApiOutboundChannel channel = require(channelCode);
        channel.setStatus(status);
        channel.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(channel);
        log.info("出站渠道状态变更：channelCode={} status={}", channelCode, status);
    }

    /** 维护凭证：明文 JSON → 加密 → 只存密文。 */
    public void saveCredential(String channelCode, Map<String, Object> credential) {
        ApiOutboundChannel channel = require(channelCode);
        if (credential == null || credential.isEmpty()) {
            throw new BizException(OpenapiErrorCode.CONFIG_ERROR, "凭证内容不能为空");
        }
        String plain = JsonUtils.toJson(credential);
        channel.setCredentialRef(credentialCrypto.encrypt(plain));
        channel.setUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(channel);
        // 只记录渠道编码与字段数量，绝不打印凭证内容
        log.info("出站渠道凭证已更新：channelCode={} 字段数={}", channelCode, credential.size());
    }

    /** 取解密后的凭证（仅出站链路内部使用，绝不经 controller 返回）。 */
    public Map<String, Object> resolveCredential(String channelCode) {
        ApiOutboundChannel channel = require(channelCode);
        if (channel.getCredentialRef() == null || channel.getCredentialRef().isBlank()) {
            throw new BizException(OpenapiErrorCode.CREDENTIAL_MISSING);
        }
        try {
            String plain = credentialCrypto.decrypt(channel.getCredentialRef());
            Map<String, Object> map = JsonUtils.fromJson(plain,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                    });
            return map == null ? Map.of() : map;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("渠道凭证解密失败：channelCode={}", channelCode, e);
            throw new BizException(OpenapiErrorCode.CONFIG_ERROR, "渠道凭证解密失败，请检查主密钥与密文是否匹配");
        }
    }

    /** 删除渠道（逻辑删）。 */
    public void delete(String channelCode) {
        ApiOutboundChannel channel = require(channelCode);
        channelMapper.deleteById(channel.getId());
        log.info("出站渠道已删除：channelCode={}", channelCode);
    }

    /** 内部：按编码取启用渠道，未找到/停用抛业务异常。 */
    public ApiOutboundChannel requireEnabled(String channelCode) {
        ApiOutboundChannel channel = channelMapper.selectByCode(channelCode);
        if (channel == null || channel.getStatus() == null || channel.getStatus() != 1) {
            throw new BizException(OpenapiErrorCode.CHANNEL_NOT_FOUND);
        }
        return channel;
    }

    private ApiOutboundChannel require(String channelCode) {
        ApiOutboundChannel channel = channelMapper.selectByCode(channelCode);
        if (channel == null) {
            throw new BizException(OpenapiErrorCode.CHANNEL_NOT_FOUND);
        }
        return channel;
    }

    private ChannelVO toVO(ApiOutboundChannel c) {
        ChannelVO vo = new ChannelVO();
        vo.setChannelCode(c.getChannelCode());
        vo.setChannelName(c.getChannelName());
        vo.setBaseUrl(c.getBaseUrl());
        vo.setAuthType(c.getAuthType());
        vo.setAuthConfig(c.getAuthConfig());
        vo.setTimeoutMs(c.getTimeoutMs());
        vo.setRetryMax(c.getRetryMax());
        vo.setAllowedApps(c.getAllowedApps());
        vo.setStatus(c.getStatus());
        vo.setRemark(c.getRemark());
        vo.setCredentialConfigured(c.getCredentialRef() != null && !c.getCredentialRef().isBlank());
        return vo;
    }
}

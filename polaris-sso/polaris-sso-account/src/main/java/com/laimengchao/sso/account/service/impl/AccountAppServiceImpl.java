package com.laimengchao.sso.account.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.laimengchao.sso.account.domain.entity.SsoAccountApp;
import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.dto.response.ApplicationVO;
import com.laimengchao.sso.account.mapper.SsoAccountAppMapper;
import com.laimengchao.sso.account.mapper.SsoAccountMapper;
import com.laimengchao.sso.account.mapper.SsoApplicationMapper;
import com.laimengchao.sso.account.service.AccountAppService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountAppServiceImpl implements AccountAppService {

    private final SsoAccountAppMapper accountAppMapper;
    private final SsoApplicationMapper applicationMapper;
    private final SsoAccountMapper accountMapper;

    @Override
    public List<Long> appIds(Long accountId) {
        return accountAppMapper.selectAppIdsByAccount(accountId);
    }

    @Override
    public List<ApplicationVO> appsOf(Long accountId) {
        List<Long> ids = accountAppMapper.selectAppIdsByAccount(accountId);
        if (ids.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<SsoApplication> w = new LambdaQueryWrapper<>();
        w.in(SsoApplication::getId, ids)
                .eq(SsoApplication::getStatus, 1)
                .orderByAsc(SsoApplication::getSort).orderByAsc(SsoApplication::getId);
        return applicationMapper.selectList(w).stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grant(Long accountId, List<Long> appIds) {
        if (accountMapper.selectById(accountId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "账户不存在");
        }
        // 去重并剔除空值，避免重复插入撞复合主键
        Set<Long> wanted = new LinkedHashSet<>();
        if (appIds != null) {
            for (Long id : appIds) {
                if (id != null) {
                    wanted.add(id);
                }
            }
        }
        // 只允许授权真实存在的应用（已停用的也允许保留授权，等启用后自然生效）
        if (!wanted.isEmpty()) {
            List<SsoApplication> found = applicationMapper.selectBatchIds(wanted);
            if (found.size() != wanted.size()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "存在无效的应用 ID");
            }
        }

        accountAppMapper.delete(new LambdaQueryWrapper<SsoAccountApp>()
                .eq(SsoAccountApp::getAccountId, accountId));
        for (Long appId : wanted) {
            SsoAccountApp link = new SsoAccountApp();
            link.setAccountId(accountId);
            link.setAppId(appId);
            accountAppMapper.insert(link);
        }
    }

    @Override
    public List<String> enabledCodes(Long accountId) {
        return accountAppMapper.selectEnabledCodesByAccount(accountId);
    }

    private ApplicationVO toVO(SsoApplication app) {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(app.getId());
        vo.setCode(app.getCode());
        vo.setName(app.getName());
        vo.setBaseUrl(app.getBaseUrl());
        vo.setIcon(app.getIcon());
        vo.setRemark(app.getRemark());
        vo.setStatus(app.getStatus());
        vo.setSort(app.getSort());
        vo.setCreatedAt(app.getCreatedAt());
        return vo;
    }
}
